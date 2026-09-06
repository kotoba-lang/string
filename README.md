# kotoba-lang/string

**Portable `clojure.string`-equivalent string manipulation.**

`(:require [kotoba.string :as str])` — one `.cljc` namespace, zero external
runtime dependencies, runs on JVM / ClojureScript / nbb.

## Why this exists

`clojure.string` (16,798 require sites across this workspace, the single
largest external-namespace dependency by usage count) has no first-party
kotoba-lang replacement — not even `kotoba-lang/fs`, whose own source still
requires `clojure.string` internally. See
[`adr-2809061500-clojure-namespace-to-kotoba-stdlib`](https://github.com/com-junkawasaki/root/blob/main/90-docs/adr/2809061500-clojure-namespace-to-kotoba-stdlib.edn)
for the full decision and the seven other `clojure.*` namespaces it plans
for (`clojure.test`, `clojure.edn`, `clojure.set`, `clojure.walk`,
`clojure.pprint`, `clojure.java.io`, `clojure.java.shell`).

## Design

Regex-backed operations (`split`, `replace`, `replace-first`) dispatch to
each host's **own native regex engine** — `java.util.regex` on the JVM,
`RegExp` on ClojureScript/nbb — via `#?(:clj ... :cljs ...)` branches, the
same shape the upstream `clojure.string.clj` / `clojure.string.cljs` pair
uses. This is deliberate: a hand-rolled regex matcher, written once and
tested against a finite suite, risks diverging from either host's real
regex engine on inputs the suite didn't think to cover. Delegating to the
host's own engine means the behavior *is* whatever that engine does — no
second implementation to keep in sync. Every non-regex function (`join`,
`trim`, `blank?`, `capitalize`, `reverse`, `index-of`, ...) is ordinary
host-neutral `clojure.core`.

## Surface

```clojure
(str/blank? nil)                          ;=> true
(str/join ["a" "b" "c"])                  ;=> "abc"
(str/join "," ["a" "b" "c"])               ;=> "a,b,c"
(str/split "a,b,,c" #",")                  ;=> ["a" "b" "" "c"]
(str/split "one two three" #" " 2)         ;=> ["one" "two three"]
(str/split-lines "a\nb\r\nc")              ;=> ["a" "b" "c"]
(str/replace "b_b_b" "_" "-")              ;=> "b-b-b"          ; literal match
(str/replace "a1b2c" #"[0-9]" "-")         ;=> "a-b-c"          ; regex match
(str/replace "abc" #"[ac]" str/upper-case) ;=> "AbC"            ; regex + fn
(str/replace-first "b_b_b" "_" "-")        ;=> "b-b_b"
(str/trim "  hi  ")                        ;=> "hi"
(str/triml "  hi  ")                       ;=> "hi  "
(str/trimr "  hi  ")                       ;=> "  hi"
(str/upper-case "hi") (str/lower-case "HI") (str/capitalize "hi there")
(str/reverse "hello")                      ;=> "olleh"
(str/starts-with? "hello" "he")
(str/ends-with? "hello" "lo")
(str/includes? "hello" "ell")
(str/index-of "hello" "l")                 ;=> 2
(str/last-index-of "hello" "l")            ;=> 3
(str/escape "a&b" {\& "&amp;"})            ;=> "a&amp;b"
```

## Supported `replace`/`replace-first` shapes

- literal string/char match → literal string/char replacement (not a regex,
  even if the string looks like one)
- `Pattern`/`RegExp` match → string replacement (the replacement is **literal
  text**; group references like `$1` are **not** expanded — that is a
  separate feature this library does not implement, to avoid a formatter
  that silently passes an unrecognized directive through)
- `Pattern`/`RegExp` match → function replacement (the function receives the
  matched string when the pattern has no capture groups, or a vector of the
  captured groups when it does)

Anything else is not a supported shape.

## Not `clojure.string/replace`'s full API

No `re-quote-replacement`. No implicit group-reference expansion in string
replacements. These are omissible without becoming a silent-approximation
trap: every call site this workspace's callers actually need is one of the
three shapes above (measured against `clojure.string/replace` usage patterns
during the survey that produced this repository).

## Verify

```sh
clojure -M:test                            # JVM
npx nbb@1.4.210 --classpath src:test run-tests.cljs   # nbb / ClojureScript
```

Both run the **same** `.cljc` suite: `13 tests, 56 assertions, 0 failures`.
