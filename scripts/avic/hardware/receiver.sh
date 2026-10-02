# Source in a disposable real Termux-Avic Bash session. Never shipped as product UI.
# These tests contain synthetic constants and persist only results/lengths.
[[ "$HOME" == /data/data/com.termuxavic/files/home && "$PREFIX" == /data/data/com.termuxavic/files/usr ]] || {
    printf 'Wrong personal environment; refusing tests.\n'; return 1;
}
PS1='avic> '
HISTFILE=/dev/null
avic_results="$HOME/avic-hardware-results.tsv"

avic_record() {
    printf '%s\t%s\t%s\n' "$1" "$2" "${3:-0}" | tee -a "$avic_results"
    curl --silent --show-error --max-time 2 --data-urlencode "case=$1" \
        --data-urlencode "result=$2" --data-urlencode "length=${3:-0}" \
        http://127.0.0.1:8765/result >/dev/null || true
}

avic_read() {
    local name="$1" expected actual
    case "$name" in
        basic) expected='hello world123';;
        spaces) expected='hello  world';;
        punctuation) expected='hello, world!';;
        suggestion) expected='hello ';;
        autocorrect) expected='world ';;
        reject) expected='wprld ';;
        capital) expected='Hello ';;
        glide) expected='hello world';;
        unicode) expected='café 😀';;
        delete|output|rotation|reload|legacy|character|session_a) expected='hello';;
        session_b) expected='world';;
        long) expected='pneumonoultramicroscopicsilicovolcanoconiosis';;
        *) printf 'Unknown synthetic case.\n'; return 1;;
    esac
    printf 'Synthetic %s > ' "$name"
    IFS= read -r actual || return
    if [[ "$actual" == "$expected" ]]; then
        avic_record "$name" PASS "${#actual}"
    else
        avic_record "$name" FAIL "${#actual}"
    fi
    unset actual
}

avic_output() {
    (sleep 1; for ((avic_i=0; avic_i<50; avic_i++)); do printf 'synthetic output\n'; sleep .1; done) &
    avic_read output
    wait
}

avic_controls_raw() {
    local original actual
    original=$(stty -g)
    printf 'Synthetic 38-byte extra-key replay > '
    stty raw -echo
    actual=$(dd bs=1 count=38 status=none | od -An -tx1 | tr -d ' \n')
    stty "$original"
    [[ "$actual" == 03040c1a1b621b091b5b411b5b421b5b441b5b431b5b481b5b461b5b4603637573746f6d7f0d ]] \
        && avic_record raw_controls PASS 38 || avic_record raw_controls FAIL
    unset actual
    printf '\n'
}

avic_echo_off() {
    local actual
    stty -echo
    printf 'Synthetic echo-off check > '
    IFS= read -r actual
    stty echo
    [[ "$actual" == hello ]] && avic_record echo_off PASS || avic_record echo_off FAIL
    unset actual
    printf '\n'
}

avic_environment() {
    local command
    for command in bash apt dpkg pkg termux-reload-settings; do
        [[ "$(command -v "$command")" == "$PREFIX/bin/$command" ]] || { avic_record environment FAIL; return 1; }
    done
    [[ -d "$HOME" && -d "$PREFIX" && -f "$PREFIX/etc/apt/sources.list" ]] || { avic_record environment FAIL; return 1; }
    avic_record environment PASS
    printf 'Bash: %s\nHOME: %s\nPREFIX: %s\n' "$BASH_VERSION" "$HOME" "$PREFIX"
}
avic_environment
printf 'Disposable full-app physical tests ready.\n'
