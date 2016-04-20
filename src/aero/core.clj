;; Copyright © 2015, JUXT LTD.

(ns aero.core
  (:require [clojure
             [edn :as edn]
             [string :refer [trim]]
             [walk :refer [walk postwalk]]]
            [clojure.java
             [io :as io]
             [shell :as sh]]))

(declare read-config)

(defmulti reader (fn [opts tag value] tag))

(defmethod reader :default
  [_ tag value]
  (if tag
    (with-meta value {::tag tag})
    value))

(defmethod reader 'env
  [opts tag value]
  (System/getenv (str value)))

(defmethod reader 'or
  [opts tag value]
  (first (filter some? value)))

(defmethod reader 'profile
  [{:keys [profile]} tag value]
  (cond (contains? value profile) (clojure.core/get value profile)
        (contains? value :default) (clojure.core/get value :default)
        :otherwise nil))

;; Deprecated
(defmethod reader 'cond
  [opts tag value]
  (reader opts 'profile value))

(defmethod reader 'hostname
  [{:keys [hostname]} tag value]
  (let [hostn (or hostname (-> (sh/sh "hostname") :out trim))]
    (or
     (some (fn [[k v]]
             (when (or (= k hostn)
                       (and (set? k) (contains? k hostn)))
               v))
           value)
     (get value :default))))

(defmethod reader 'user
  [{:keys [user]} tag value]
  (let [user (or user (-> (sh/sh "whoami") :out trim))]
    (or
     (some (fn [[k v]]
             (when (or (= k user)
                       (and (set? k) (contains? k user)))
               v))
           value)
     (get value :default))))

(defmethod reader 'include
  [opts tag value]
  (read-config value opts))

;; Deprecated
(defmethod reader 'file
  [opts tag value]
  (read-config value opts))

(defmethod reader 'join
  [opts tag value]
  (apply str value))

(defn read-config
  "Optional second argument is a map. Keys are :profile, indicating the
  profile for use with #cond"
  ([r opts]
   (let [config
         (with-open [pr (java.io.PushbackReader. (io/reader r))]
           (edn/read
            {:eof nil
             :default (partial reader (merge {:profile :default} opts))}
            pr))]
     (postwalk (fn [v]
                 (if-not (contains? (meta v) :ref)
                   v
                   (recur (get-in config v))))
               config)))
  ([r] (read-config r {})))
