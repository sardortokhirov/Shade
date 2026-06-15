import pathlib
p = pathlib.Path("src/main/java/com/example/shade/service/TopUpService.java")
lines = p.read_text(encoding="utf-8").splitlines(keepends=True)
inserted = False
for i, line in enumerate(lines):
    if (line == '            sessionService.setUserState(chatId, "TOPUP_AWAITING_SCREENSHOT");\n'
 and i + 2 < len(lines)
            and lines[i + 1].strip() == ""
            and "String number = blockedUserRepository.findByChatId(request.getChatId())" in lines[i + 2]
            and "bindPendingScreenshotRequest" not in lines[i + 1]
            and (i == 0 or "Osonda" not in lines[i + 5])):
        lines.insert(i + 2, "            bindPendingScreenshotRequest(chatId, request);\n")
        inserted = True
        break
if not inserted:
    # Fallback: first occurrence only in verifyPayment catch (before adminLog Osonda)
    for i, line in enumerate(lines):
        if line.strip() == 'sessionService.setUserState(chatId, "TOPUP_AWAITING_SCREENSHOT");':
            if i + 2 < len(lines) and "Osonda Xatolik" in "".join(lines[i : i + 25]):
                if "bindPendingScreenshotRequest" not in lines[i + 1] and lines[i + 1].strip() == "":
                    lines.insert(i + 2, "            bindPendingScreenshotRequest(chatId, request);\n")
                    inserted = True
                    break
if not inserted:
    raise SystemExit("insert failed")
p.write_text("".join(lines), encoding="utf-8")
print("ok")
