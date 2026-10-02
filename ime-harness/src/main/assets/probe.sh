#!/system/bin/sh
# Only PASS/FAIL/length results are persisted. Never save entered text.
printf '\033[2J\033[H'
if [ "$4" = keys ]; then
    printf 'Synthetic terminal key-byte replay\r\nCtrl+C/D/L/Z, Alt+B, Esc, Tab, Up/Down/Left/Right,\r\nHome, End, popup End, C-c macro, custom, Backspace, Enter\r\n'
    saved_mode=$(stty -g)
    trap 'stty "$saved_mode"' EXIT
    stty raw -echo
    actual=$(od -An -tx1 -N 38 | tr -d ' \r\n')
    stty "$saved_mode"
    if [ "$actual" = "$1" ]; then result=PASS; else result=FAIL; fi
    printf '%s fixture=%s bytes=%s\n' "$result" "$3" "$(( ${#actual} / 2 ))" >> "$2"
    printf '\r\nRESULT %s (38 terminal key bytes expected)\r\n' "$result"
    exit
fi
printf 'Synthetic input fixture\r\nExpected: %s\r\n> ' "$1"
if [ "$4" = noise ]; then
    # Bounded synthetic output, independent of input. Never inspect terminal text/history.
    (
        i=0
        while [ "$i" -lt 160 ]; do
            printf '\r\nbackground output %s\r\n' "$i"
            i=$((i + 1))
            sleep 0.05
        done
    ) &
fi
while IFS= read -r line; do
    if [ "$line" = "$1" ]; then result=PASS; else result=FAIL; fi
    printf '%s fixture=%s shell_units=%s\n' "$result" "$3" "${#line}" >> "$2"
    printf '\r\nRESULT %s (%s shell units)\r\n> ' "$result" "${#line}"
done
