(ns kotoba.string-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.string :as str]))

(deftest blank?-test
  (is (true? (str/blank? nil)))
  (is (true? (str/blank? "")))
  (is (true? (str/blank? "   ")))
  (is (true? (str/blank? "\t\n")))
  (is (false? (str/blank? "a")))
  (is (false? (str/blank? " a "))))

(deftest includes-starts-ends-test
  (is (true? (str/includes? "hello world" "wor")))
  (is (false? (str/includes? "hello world" "xyz")))
  (is (true? (str/starts-with? "hello" "he")))
  (is (false? (str/starts-with? "hello" "lo")))
  (is (true? (str/ends-with? "hello" "lo")))
  (is (false? (str/ends-with? "hello" "he"))))

(deftest index-of-test
  (is (= 6 (str/index-of "hello world" "world")))
  (is (nil? (str/index-of "hello world" "xyz")))
  (is (= 3 (str/index-of "aXaXa" "X" 2)))
  (is (= 0 (str/index-of "hello" "h")))
  (is (= 3 (str/last-index-of "aXaXa" "X")))
  (is (= 1 (str/last-index-of "aXaXa" "X" 2))))

(deftest case-test
  (is (= "HELLO" (str/upper-case "hello")))
  (is (= "hello" (str/lower-case "HELLO")))
  (is (= "Hello world" (str/capitalize "hello world")))
  (is (= "Hello world" (str/capitalize "HELLO WORLD")))
  (is (= "H" (str/capitalize "h")))
  (is (= "" (str/capitalize "")))
  (is (= "olleh" (str/reverse "hello"))))

(deftest trim-test
  (is (= "hello" (str/trim "  hello  ")))
  (is (= "hello  " (str/triml "  hello  ")))
  (is (= "  hello" (str/trimr "  hello  ")))
  (is (= "hello" (str/trim-newline "hello\n\r\n")))
  (is (= "" (str/trim "   "))))

(deftest join-test
  (is (= "abc" (str/join ["a" "b" "c"])))
  (is (= "a,b,c" (str/join "," ["a" "b" "c"])))
  (is (= "" (str/join [])))
  (is (= "" (str/join "," []))))

(deftest split-test
  (is (= ["a" "b" "c"] (str/split "a,b,c" #",")))
  (is (= ["a" "b" "" "c"] (str/split "a,b,,c" #",")))
  (is (= ["a" "b" "c"] (str/split "a,b,c," #",")))
  (is (= ["one" "two three"] (str/split "one two three" #" " 2)))
  (is (= ["a" "b" "c"] (str/split "a1b2c" #"[0-9]")))
  (is (= ["" "a" "b"] (str/split ",a,b" #","))))

(deftest split-lines-test
  (is (= ["a" "b" "c"] (str/split-lines "a\nb\r\nc")))
  (is (= ["one line"] (str/split-lines "one line"))))

(deftest replace-literal-test
  (is (= "b-b-b" (str/replace "b_b_b" "_" "-")))
  (is (= "xyz" (str/replace "abc" "abc" "xyz")))
  (is (= "aXbXc" (str/replace "a b c" " " "X")))
  (is (= "no match here" (str/replace "no match here" "zzz" "-"))))

(deftest replace-first-literal-test
  (is (= "b-b_b" (str/replace-first "b_b_b" "_" "-")))
  (is (= "no change" (str/replace-first "no change" "zzz" "-"))))

(deftest replace-pattern-string-test
  (is (= "a-b-c" (str/replace "a1b2c" #"[0-9]" "-")))
  (is (= "XXX" (str/replace "abc" #"." "X")))
  (is (= "keep $ literal" (str/replace "keep A literal" #"A" "$")))
  (is (= "a-b2c" (str/replace-first "a1b2c" #"[0-9]" "-"))))

(deftest replace-pattern-fn-test
  (is (= "AbC" (str/replace "abc" #"[ac]" (fn [m] (str/upper-case m)))))
  (is (= "b-a" (str/replace "a-b" #"(a)-(b)" (fn [[g1 g2]] (str g2 "-" g1))))))

(deftest escape-test
  (is (= "a&amp;b" (str/escape "a&b" {\& "&amp;"})))
  (is (= "unchanged" (str/escape "unchanged" {}))))
