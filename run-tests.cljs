(ns run-tests (:require [clojure.test :as t] [kotoba.string-test]))
(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (when-not (t/successful? m) (js/process.exit 1)))
(t/run-tests 'kotoba.string-test)
