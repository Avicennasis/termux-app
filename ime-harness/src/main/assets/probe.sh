#!/system/bin/sh
# Only PASS/FAIL/length results are persisted. Never save entered text.
printf '\033[2J\033[H'
printf 'Synthetic input fixture\r\nExpected: %s\r\n> ' "$1"
while IFS= read -r line; do
    if [ "$line" = "$1" ]; then result=PASS; else result=FAIL; fi
    printf '%s fixture=%s shell_units=%s\n' "$result" "$3" "${#line}" >> "$2"
    printf '\r\nRESULT %s (%s shell units)\r\n> ' "$result" "${#line}"
done
