(ns kotoba.string
  "Portable clojure.string-equivalent surface -- JVM, ClojureScript, nbb.
   Regex-backed operations dispatch to each host's own native regex engine
   (java.util.regex on :clj, RegExp on :cljs) rather than a hand-rolled
   matcher, so behaviour matches what that host's regex already does. Every
   other function is host-neutral clojure.core.

   Supported shapes for `replace`/`replace-first` match/replacement pairs:
   char->char, literal-string->string, Pattern->string (replacement is
   literal text, group references like $1 are NOT expanded), Pattern->fn
   (fn receives the matched string if the pattern has no groups, or a
   vector of the groups if it does). Anything else throws rather than
   silently doing the wrong thing."
  (:refer-clojure :exclude [replace reverse]))

;; ---------------------------------------------------------------- predicates

(defn blank?
  "True if s is nil, empty, or contains only whitespace."
  [s]
  (or (nil? s) (nil? (re-find #"\S" s))))

(defn includes?
  [s substr]
  #?(:clj  (.contains ^String s ^String substr)
     :cljs (not= -1 (.indexOf s substr))))

(defn starts-with?
  [s prefix]
  #?(:clj  (.startsWith ^String s ^String prefix)
     :cljs (zero? (.indexOf s prefix))))

(defn ends-with?
  [s suffix]
  #?(:clj  (.endsWith ^String s ^String suffix)
     :cljs (let [i (- (count s) (count suffix))]
             (and (>= i 0) (= i (.indexOf s suffix i))))))

;; -------------------------------------------------------------------- index

(defn index-of
  ([s value] (index-of s value 0))
  ([s value from-index]
   (let [needle #?(:clj (str value) :cljs (str value))
         i #?(:clj  (.indexOf ^String s ^String needle (int from-index))
              :cljs (.indexOf s needle from-index))]
     (when (>= i 0) i))))

(defn last-index-of
  ([s value] (last-index-of s value (count s)))
  ([s value from-index]
   (let [needle (str value)
         i #?(:clj  (.lastIndexOf ^String s ^String needle (int from-index))
              :cljs (.lastIndexOf s needle from-index))]
     (when (>= i 0) i))))

;; --------------------------------------------------------------- transforms

(defn upper-case [s] #?(:clj (.toUpperCase ^String s) :cljs (.toUpperCase s)))
(defn lower-case [s] #?(:clj (.toLowerCase ^String s) :cljs (.toLowerCase s)))

(defn capitalize
  [s]
  (if (< (count s) 2)
    (upper-case s)
    (str (upper-case (subs s 0 1)) (lower-case (subs s 1)))))

(defn reverse
  [s]
  (apply str (clojure.core/reverse s)))

(defn trim
  [s]
  #?(:clj  (.trim ^String s)
     :cljs (.trim s)))

(defn triml
  [s]
  (let [i (or (re-find #"\S" s) nil)]
    (loop [idx 0]
      (if (or (>= idx (count s)) (not (re-matches #"\s" (str (nth s idx)))))
        (subs s idx)
        (recur (inc idx))))))

(defn trimr
  [s]
  (loop [idx (count s)]
    (if (or (zero? idx) (not (re-matches #"\s" (str (nth s (dec idx))))))
      (subs s 0 idx)
      (recur (dec idx)))))

(defn trim-newline
  [s]
  (loop [idx (count s)]
    (if (zero? idx)
      (subs s 0 idx)
      (let [c (nth s (dec idx))]
        (if (or (= c \newline) (= c \return))
          (recur (dec idx))
          (subs s 0 idx))))))

;; --------------------------------------------------------------------- join

(defn join
  ([coll] (apply str coll))
  ([separator coll] (apply str (interpose separator coll))))

;; -------------------------------------------------------------------- split

(defn- strip-trailing-empty
  [v]
  (vec (clojure.core/reverse (drop-while #(= "" %) (clojure.core/reverse v)))))

(defn- split-cljs-limited
  [s gre limit]
  (loop [last-end 0
         n 1
         out []]
    (set! (.-lastIndex gre) last-end)
    (let [m (.exec gre s)]
      (if (or (nil? m) (>= n limit) (> last-end (count s)))
        (conj out (subs s last-end))
        (let [start (.-index m)
              matched (aget m 0)
              end (if (zero? (count matched)) (inc start) (+ start (count matched)))]
          (recur end (inc n) (conj out (subs s last-end start))))))))

(defn split
  ([s re] (split s re 0))
  ([s re limit]
   #?(:clj
      (vec (.split ^java.util.regex.Pattern re ^String s (int limit)))
      :cljs
      (let [src (.-source re)
            fl  (.-flags re)]
        (if (zero? limit)
          (strip-trailing-empty (vec (.split s (js/RegExp. src fl))))
          (split-cljs-limited s (js/RegExp. src (if (includes? fl "g") fl (str fl "g"))) limit))))))

(defn split-lines
  [s]
  (split s #"\r\n|\r|\n"))

;; ------------------------------------------------------------------ replace

(defn- literal-replace-all
  [s match replacement]
  #?(:clj  (.replace ^String s ^CharSequence (str match) ^CharSequence (str replacement))
     :cljs (.join (.split s (str match)) (str replacement))))

(defn- literal-replace-first
  [s match replacement]
  (let [match (str match) replacement (str replacement)
        i (index-of s match)]
    (if (nil? i)
      s
      (str (subs s 0 i) replacement (subs s (+ i (count match)))))))

(defn- pattern? [x]
  #?(:clj (instance? java.util.regex.Pattern x) :cljs (instance? js/RegExp x)))

#?(:clj
   (defn- clj-groups
     "Vector of capture-group strings 1..N from a matched java.util.regex.Matcher."
     [m]
     (let [n (.groupCount m)]
       (vec (map (fn [i] (.group m (inc i))) (range n))))))

#?(:clj
   (defn- pattern-replace-clj
     [s re replacement first-only?]
     (let [m (.matcher ^java.util.regex.Pattern re ^String s)
           fn? (ifn? replacement)
           sb (StringBuilder.)]
       (loop [found (.find m)]
         (if (not found)
           (do (.appendTail m sb) (str sb))
           (let [groups (clj-groups m)
                 matched-str (.group m)
                 rep (if fn?
                       (str (replacement (if (empty? groups) matched-str groups)))
                       (str replacement))]
             (.appendReplacement m sb (java.util.regex.Matcher/quoteReplacement rep))
             (if first-only?
               (do (.appendTail m sb) (str sb))
               (recur (.find m)))))))))

#?(:cljs
   (defn- cljs-groups
     "Vector of capture-group strings 1..N from a JS RegExp exec match array."
     [m]
     (vec (for [i (range 1 (.-length m))] (aget m i)))))

#?(:cljs
   (defn- pattern-replace-cljs
     [s re replacement first-only?]
     (let [src (.-source re)
           fl (.-flags re)
           gre (js/RegExp. src (if (includes? fl "g") fl (str fl "g")))
           fn? (ifn? replacement)]
       (loop [last-end 0
              out ""]
         (set! (.-lastIndex gre) last-end)
         (let [m (.exec gre s)]
           (if (nil? m)
             (str out (subs s last-end))
             (let [start (.-index m)
                   matched-str (aget m 0)
                   groups (cljs-groups m)
                   rep (if fn?
                         (str (replacement (if (empty? groups) matched-str groups)))
                         (str replacement))
                   match-end (if (zero? (count matched-str))
                               (inc start)
                               (+ start (count matched-str)))
                   piece (str out (subs s last-end start) rep)]
               (if first-only?
                 (str piece (subs s match-end))
                 (recur match-end piece)))))))))

(defn- pattern-replace
  [s re replacement first-only?]
  #?(:clj  (pattern-replace-clj s re replacement first-only?)
     :cljs (pattern-replace-cljs s re replacement first-only?)))

(defn replace
  [s match replacement]
  (cond
    (pattern? match) (pattern-replace s match replacement false)
    :else (literal-replace-all s match replacement)))

(defn replace-first
  [s match replacement]
  (cond
    (pattern? match) (pattern-replace s match replacement true)
    :else (literal-replace-first s match replacement)))

(defn escape
  "Replace each char of s that is a key in cmap with its value (a string)."
  [s cmap]
  (apply str (map (fn [c] (get cmap c c)) s)))
