package com.example.shade.service;

import com.example.shade.dto.DashboardStats;
import com.example.shade.dto.RequestFilter;
import com.example.shade.dto.UserWalletBalancesResponse;
import com.example.shade.model.HizmatRequest;
import com.example.shade.model.RequestStatus;
import com.example.shade.model.RequestType;
import com.example.shade.model.UserBalance;
import com.example.shade.repository.HizmatRequestRepository;
import com.example.shade.repository.UserBalanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

        private static double requestAmount(HizmatRequest request) {
                if (request.getUniqueAmount() != null && request.getUniqueAmount() > 0) {
                        return request.getUniqueAmount();
                }
                if (request.getAmount() != null && request.getAmount() > 0) {
                        return request.getAmount();
                }
                return 0.0;
        }

        private static LocalDateTime approvalEventTime(HizmatRequest request) {
                return request.getApprovedAt() != null ? request.getApprovedAt() : request.getCreatedAt();
        }

        private static boolean isWithinApprovalRange(HizmatRequest request, LocalDateTime startDate, LocalDateTime endDate) {
                LocalDateTime eventTime = approvalEventTime(request);
                if (eventTime == null) {
                        return false;
                }
                if (startDate != null && eventTime.isBefore(startDate)) {
                        return false;
                }
                if (endDate != null && eventTime.isAfter(endDate)) {
                        return false;
                }
                return true;
        }

        @Autowired
        private HizmatRequestRepository requestRepository;

        @Autowired
        private UserBalanceRepository userBalanceRepository;

        public DashboardStats getDashboardStats(RequestFilter filter) {
                // One DB load instead of ~18 full-table scans (same filters/math as before).
                List<HizmatRequest> all = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), null, null);
                List<HizmatRequest> byCreated = filterByCreatedAt(all, filter.getStartDate(), filter.getEndDate());
                if (filter.getStatus() != null) {
                        byCreated = byCreated.stream()
                                        .filter(r -> r.getStatus() == filter.getStatus())
                                        .collect(Collectors.toList());
                }
                if (filter.getType() != null) {
                        byCreated = byCreated.stream()
                                        .filter(r -> r.getType() == filter.getType())
                                        .collect(Collectors.toList());
                }

                long totalRequests = byCreated.size();
                long approvedRequests = byCreated.stream().filter(r -> r.getStatus() == RequestStatus.APPROVED).count();
                long pendingRequests = byCreated.stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
                long pendingAdminRequests = byCreated.stream().filter(r -> r.getStatus() == RequestStatus.PENDING_ADMIN).count();
                long canceledRequests = byCreated.stream().filter(r -> r.getStatus() == RequestStatus.CANCELED).count();
                long failedRequests = byCreated.stream().filter(r -> r.getStatus() == RequestStatus.FAILED).count();

                double totalApprovedWithdrawalAmount = byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED && r.getType() == RequestType.WITHDRAWAL)
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .sum();
                double totalApprovedTopUpAmount = byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED && r.getType() == RequestType.TOP_UP)
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .sum();
                double totalApprovedTipAmount = byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED && r.getType() == RequestType.TIP)
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .sum();
                double totalWalletToWalletFees = byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED
                                                && r.getType() == RequestType.WALLET_TO_WALLET)
                                .mapToDouble(r -> r.getFeeAmount() != null ? r.getFeeAmount() : 0.0)
                                .sum();
                double totalTicketTradeFees = byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED
                                                && r.getType() == RequestType.TICKET_TRADE)
                                .mapToDouble(r -> r.getFeeAmount() != null ? r.getFeeAmount() : 0.0)
                                .sum();
                // Bonus uses approval event time (approvedAt/createdAt), same as before.
                double totalApprovedBonusAmount = all.stream()
                                .filter(r -> r.getStatus() == RequestStatus.BONUS_APPROVED)
                                .filter(r -> isWithinApprovalRange(r, filter.getStartDate(), filter.getEndDate()))
                                .mapToDouble(DashboardService::requestAmount)
                                .sum();

                Map<RequestStatus, Long> statusDistribution = byCreated.stream()
                                .collect(Collectors.groupingBy(HizmatRequest::getStatus, Collectors.counting()));
                Map<String, Long> requestsByPlatform = byCreated.stream()
                                .collect(Collectors.groupingBy(HizmatRequest::getPlatform, Collectors.counting()));
                Map<String, Long> requestsByDate = byCreated.stream()
                                .collect(Collectors.groupingBy(
                                                r -> r.getCreatedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                                                Collectors.counting()));
                Map<String, Double> amountByPlatform = byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED && r.getType() == RequestType.WITHDRAWAL)
                                .collect(Collectors.groupingBy(
                                                HizmatRequest::getPlatform,
                                                Collectors.summingDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)));
                double averageApprovedAmount = byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED && r.getType() == RequestType.WITHDRAWAL)
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .average()
                                .orElse(0.0);
                Map<Long, Long> topUsers = byCreated.stream()
                                .collect(Collectors.groupingBy(HizmatRequest::getChatId, Collectors.counting()))
                                .entrySet().stream()
                                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                                .limit(10)
                                .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                Map.Entry::getValue,
                                                (e1, e2) -> e1,
                                                HashMap::new));
                List<Map<String, Object>> recentRequests = byCreated.stream()
                                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                                .limit(5)
                                .map(r -> {
                                        Map<String, Object> map = new HashMap<>();
                                        map.put("id", r.getId());
                                        map.put("chatId", r.getChatId());
                                        map.put("platform", r.getPlatform());
                                        map.put("platformUserId", r.getPlatformUserId());
                                        map.put("fullName", r.getFullName());
                                        map.put("cardNumber", r.getCardNumber());
                                        map.put("amount", r.getUniqueAmount());
                                        map.put("status", r.getStatus());
                                        map.put("type", r.getType());
                                        map.put("createdAt", r.getCreatedAt());
                                        return map;
                                })
                                .collect(Collectors.toList());

                Map<String, Map<String, Double>> platformGraphData = new HashMap<>();
                byCreated.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED)
                                .collect(Collectors.groupingBy(HizmatRequest::getPlatform))
                                .forEach((platform, platformRequests) -> {
                                        Map<String, Double> amounts = new HashMap<>();
                                        amounts.put("withdrawal", platformRequests.stream()
                                                        .filter(r -> r.getType() == RequestType.WITHDRAWAL)
                                                        .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                                        .sum());
                                        amounts.put("top_up", platformRequests.stream()
                                                        .filter(r -> r.getType() == RequestType.TOP_UP)
                                                        .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                                        .sum());
                                        platformGraphData.put(platform, amounts);
                                });

                return new DashboardStats(totalRequests, approvedRequests, pendingRequests, pendingAdminRequests,
                                canceledRequests, failedRequests, totalApprovedWithdrawalAmount, statusDistribution,
                                requestsByPlatform,
                                requestsByDate, amountByPlatform, averageApprovedAmount, topUsers, recentRequests,
                                totalApprovedTopUpAmount, totalApprovedBonusAmount, totalApprovedTipAmount,
                                totalWalletToWalletFees, totalTicketTradeFees,
                                platformGraphData);
        }

        private static List<HizmatRequest> filterByCreatedAt(List<HizmatRequest> requests,
                        LocalDateTime startDate, LocalDateTime endDate) {
                return requests.stream()
                                .filter(r -> startDate == null || (r.getCreatedAt() != null && !r.getCreatedAt().isBefore(startDate)))
                                .filter(r -> endDate == null || (r.getCreatedAt() != null && !r.getCreatedAt().isAfter(endDate)))
                                .collect(Collectors.toList());
        }

        public long getRequestCount(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.size();
        }

        public double getTotalApprovedWithdrawalAmount(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED,
                                RequestType.WITHDRAWAL);
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .sum();
        }

        public double getTotalApprovedTopUpAmount(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.TOP_UP);
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .sum();
        }

        public double getTotalApprovedBonusAmount(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), RequestStatus.BONUS_APPROVED, null);
                return requests.stream()
                                .filter(r -> isWithinApprovalRange(r, filter.getStartDate(), filter.getEndDate()))
                                .mapToDouble(DashboardService::requestAmount)
                                .sum();
        }

        public double getTotalApprovedTipAmount(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, RequestType.TIP);
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .sum();
        }

        public Map<RequestStatus, Long> getStatusDistribution(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .collect(Collectors.groupingBy(
                                                HizmatRequest::getStatus,
                                                Collectors.counting()));
        }

        public Map<String, Long> getRequestsByPlatform(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .collect(Collectors.groupingBy(
                                                HizmatRequest::getPlatform,
                                                Collectors.counting()));
        }

        public Map<String, Long> getRequestsByDate(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .collect(Collectors.groupingBy(
                                                r -> r.getCreatedAt().toLocalDate()
                                                                .format(DateTimeFormatter.ISO_LOCAL_DATE),
                                                Collectors.counting()));
        }

        public Map<String, Double> getAmountByPlatform(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED
                                                && r.getType() == RequestType.WITHDRAWAL)
                                .collect(Collectors.groupingBy(
                                                HizmatRequest::getPlatform,
                                                Collectors.summingDouble(
                                                                r -> r.getUniqueAmount() != null ? r.getUniqueAmount()
                                                                                : 0.0)));
        }

        public double getAverageApprovedAmount(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED
                                                && r.getType() == RequestType.WITHDRAWAL)
                                .mapToDouble(r -> r.getUniqueAmount() != null ? r.getUniqueAmount() : 0.0)
                                .average()
                                .orElse(0.0);
        }

        public Map<Long, Long> getTopUsersByRequestCount(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .collect(Collectors.groupingBy(
                                                HizmatRequest::getChatId,
                                                Collectors.counting()))
                                .entrySet().stream()
                                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                                .limit(10)
                                .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                Map.Entry::getValue,
                                                (e1, e2) -> e1,
                                                HashMap::new));
        }

        public List<Map<String, Object>> getRecentRequests(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), filter.getStatus(), filter.getType());
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                return requests.stream()
                                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                                .limit(5)
                                .map(r -> {
                                        Map<String, Object> map = new HashMap<>();
                                        map.put("id", r.getId());
                                        map.put("chatId", r.getChatId());
                                        map.put("platform", r.getPlatform());
                                        map.put("platformUserId", r.getPlatformUserId());
                                        map.put("fullName", r.getFullName());
                                        map.put("cardNumber", r.getCardNumber());
                                        map.put("amount", r.getUniqueAmount());
                                        map.put("status", r.getStatus());
                                        map.put("type", r.getType());
                                        map.put("createdAt", r.getCreatedAt());
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        public Map<String, Map<String, Double>> getPlatformGraphData(RequestFilter filter) {
                List<HizmatRequest> requests = requestRepository.findByFilters(
                                filter.getCardId(), filter.getPlatformId(), RequestStatus.APPROVED, null);
                if (filter.getStartDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isBefore(filter.getStartDate()))
                                        .collect(Collectors.toList());
                }
                if (filter.getEndDate() != null) {
                        requests = requests.stream()
                                        .filter(r -> !r.getCreatedAt().isAfter(filter.getEndDate()))
                                        .collect(Collectors.toList());
                }
                Map<String, Map<String, Double>> result = new HashMap<>();
                requests.stream()
                                .filter(r -> r.getStatus() == RequestStatus.APPROVED)
                                .collect(Collectors.groupingBy(HizmatRequest::getPlatform))
                                .forEach((platform, platformRequests) -> {
                                        Map<String, Double> amounts = new HashMap<>();
                                        double withdrawalAmount = platformRequests.stream()
                                                        .filter(r -> r.getType() == RequestType.WITHDRAWAL)
                                                        .mapToDouble(r -> r.getUniqueAmount() != null
                                                                        ? r.getUniqueAmount()
                                                                        : 0.0)
                                                        .sum();
                                        double topUpAmount = platformRequests.stream()
                                                        .filter(r -> r.getType() == RequestType.TOP_UP)
                                                        .mapToDouble(r -> r.getUniqueAmount() != null
                                                                        ? r.getUniqueAmount()
                                                                        : 0.0)
                                                        .sum();
                                        amounts.put("withdrawal", withdrawalAmount);
                                        amounts.put("top_up", topUpAmount);
                                        result.put(platform, amounts);
                                });
                return result;
        }

        /**
         * Returns all users' wallet balances and total for dashboard.
         */
        public UserWalletBalancesResponse getAllUsersWalletMoney() {
                List<UserBalance> all = userBalanceRepository.findAll();
                List<UserWalletBalancesResponse.UserWalletBalanceItem> items = all.stream()
                                .map(ub -> UserWalletBalancesResponse.UserWalletBalanceItem.builder()
                                                .chatId(ub.getChatId())
                                                .walletBalance(ub.getWalletBalance() != null ? ub.getWalletBalance() : 0L)
                                                .build())
                                .collect(Collectors.toList());
                long total = items.stream()
                                .mapToLong(UserWalletBalancesResponse.UserWalletBalanceItem::getWalletBalance)
                                .sum();
                return UserWalletBalancesResponse.builder()
                                .totalWalletMoney(total)
                                .userBalances(items)
                                .build();
        }
}