(ns reagent-datatable.table-utils
  (:require
   [medley.core :as medley]
   [clojure.string :as str]))

(defn process-string-for-filtering
  "Removes excess whitespace and converts to lowercase."
  [s]
  (some-> s
          str/trim
          not-empty
          str/lower-case
          (str/replace #"\s+" " ")))

(defn reset-pagination
  [table]
  (assoc-in table [:pagination :current-page] 0))

(defn column-sort
  [table-atom column-key]
  (swap! table-atom
         #(-> %
              reset-pagination
              (update-in [:columns :sort]
                         (fn [[curr-key order-bool]]
                           (if (= curr-key column-key)
                             [curr-key (not order-bool)]
                             [column-key true]))))))

(defn column-sort-value
  [table]
  (get-in table [:columns :sort]))

(defn column-filter-value
  [table column-key]
  (get-in table [:columns :filter-input column-key]))

(defn column-filter-values
  [table]
  (let [filter-input (->> (get-in table [:columns :filter-input])
                          (medley/map-vals process-string-for-filtering)
                          (medley/remove-vals nil?)
                          (into {}))
        select-input (medley/filter-vals seq (get-in table [:columns :filter-select]))]
    (merge filter-input select-input)))

(defn column-filter-on-change
  [evt table-atom column-key]
  (swap! table-atom
         #(-> %
              reset-pagination
              (assoc-in [:columns :filter-input column-key]
                        (-> evt .-target .-value)))))

(defn column-filter-reset
  ([table-atom column-key]
   (swap! table-atom update-in [:columns :filter-input] dissoc column-key))
  ([table-atom column-key value]
   (swap! table-atom #(-> %
                          (update-in [:columns :filter-input] dissoc column-key)
                          (update-in [:columns :filter-select column-key] disj value)))))

(defn column-filters?
  [table column-key]
  (get-in table [:columns :column-filters?] column-key))

(defn column-select-input?
  [table column-key]
  (= :select (get-in table [:columns :column-filters column-key])))

(defn column-select-filter-options
  [table column-key]
  (->> (get-in table [:rows :data])
       (map (fn [row]
              (get row column-key)))
       (set)))

(defn column-select-filter-on-change
  [table-atom value column-key]
  (swap! table-atom
         (fn [table]
           (let [curr-value (get-in table [:columns :filter-select column-key value])]
             (if (seq curr-value)
               (-> table
                   reset-pagination
                   (update-in [:columns :filter-select column-key] disj curr-value))
               (-> table
                   reset-pagination
                   (update-in [:columns :filter-select column-key] (fnil conj #{}) value)))))))

(defn column-select-filter-value
  [table column-key value]
  (get-in table [:columns :filter-select column-key value] false))

(defn column-filter-reset-all
  [table-atom]
  (swap! table-atom
         (fn [table]
           (-> table
               reset-pagination
               (update :columns dissoc :filter-select)
               (update :columns dissoc :filter-input)))))

(defn search-all-value
  [table]
  (get-in table [:head :search-all]))

(defn search-all-on-change
  [evt table-atom]
  (swap! table-atom
         #(-> %
              reset-pagination
              (assoc-in [:head :search-all]
                        (-> evt .-target .-value)))))

(defn search-all-reset
  [table-atom]
  (swap! table-atom
         #(-> %
              reset-pagination
              (update :head dissoc :search-all))))


(defn block-filter-values
  [{:keys [columns]}]
  (concat
   (remove (comp empty? second) (:filter-input columns))
   (->> columns
        :filter-select
        (medley/filter-vals seq)
        ;; build a vector of filters with each filter taking the form of
        ;; [:field-key "filter-term"]
        (reduce (fn [col [k filter-set]]
                  (apply conj col
                         (for [filter filter-set]
                           [k filter])))
                nil)
        (remove empty?))))

(defn column-visible?
  [table column-key]
  (contains? (get-in table [:columns :hidden]) column-key))

(defn column-visibility-on-change
  [table-atom column-key]
  (swap! table-atom #(-> %
                         reset-pagination
                         (update-in [:columns :filter-select] dissoc column-key)
                         (update-in [:columns :filter-input] dissoc column-key)
                         (update-in [:columns :hidden]
                                    (if (column-visible? @table-atom column-key) disj conj)
                                    column-key))))

(defn table-columns
  [table]
  (let [columns (get-in table [:columns :data])
        hidden (get-in table [:columns :hidden])]
    (remove #(contains? hidden (:column-key %)) columns)))

(defn pagination-rows-per-page-on-change
  [evt table-atom]
  (swap! table-atom
         #(-> %
              (assoc-in [:pagination :rows-per-page]
                        (js/parseInt (-> evt .-target .-value)))
              (assoc-in [:pagination :current-page] 0))))

(defn pagination-rows-per-page
  [table]
  (or (get-in table [:pagination :rows-per-page]) 15))

(defn pagination-current-page
  [table]
  (or (get-in table [:pagination :current-page]) 0))

(defn pagination-current-and-total-pages
  [table processed-rows]
  (let [offset (pagination-current-page table)
        rows-per-page (pagination-rows-per-page table)
        nth-rows-at-page (+ rows-per-page (* offset rows-per-page))
        nth-rows (count processed-rows)]
    (str (inc (* offset rows-per-page))
         "-"
         (if (> nth-rows-at-page nth-rows)
           nth-rows
           nth-rows-at-page)
         " of "
         nth-rows)))

(defn pagination-rows-exhausted?
  [table processed-rows]
  (let [current-page (pagination-current-page table)
        rows-per-page (pagination-rows-per-page table)
        tot-rows (count processed-rows)
        left-rows (- tot-rows (* rows-per-page
                                 current-page)
                     rows-per-page)]
    (or (zero? left-rows) (neg? left-rows))))

(defn pagination-inc-page
  [table-atom processed-rows]
  (when-not (pagination-rows-exhausted? @table-atom processed-rows)
    (swap! table-atom update-in [:pagination :current-page]
           (fnil inc 0))))

(defn pagination-dec-page
  [table-atom]
  (when (> (pagination-current-page @table-atom) 0)
    (swap! table-atom update-in [:pagination :current-page]
           dec)))


(defn date?
  "Returns true if the argument is a date, false otherwise."
  [d]
  (instance? js/Date d))

(defn date-as-sortable
  "Returns something that can be used to order dates."
  [d]
  (.getTime d))

(defn compare-vals
  "A comparator that works for the various types found in table structures.
  This is a limited implementation that expects the arguments to be of
  the same type. The :else case is to call compare, which will throw
  if the arguments are not comparable to each other or give undefined
  results otherwise.
  Both arguments can be a vector, in which case they must be of equal
  length and each element is compared in turn."
  [x y]
  (cond
    (and (vector? x)
         (vector? y)
         (= (count x) (count y)))
    (reduce #(let [r (compare (first %2) (second %2))]
               (if (not= r 0)
                 (reduced r)
                 r))
            0
            (map vector x y))

    (or (and (number? x) (number? y))
        (and (string? x) (string? y))
        (and (boolean? x) (boolean? y)))
    (compare x y)

    (and (date? x) (date? y))
    (compare (date-as-sortable x) (date-as-sortable y))

    :else ;; hope for the best... are there any other possiblities?
    (compare (str x) (str y))))

(defn resolve-sorting
  [table rows]
  (if-let [[column-key order] (column-sort-value table)]
    (sort
     (fn [row1 row2]
       (let [val1 (column-key row1)
             val2 (column-key row2)]
         (if order
           (compare-vals val2 val1)
           (compare-vals val1 val2))))
     rows)
    ;; with no sorting return rows input
    rows))

(defn resolve-column-filtering
  [table rows]
  (if-let [column-filters (column-filter-values table)]
    (filter
     (fn [row-data-map]
       (every?
        (fn [[k v]]
          (let [render-fn (or (:render-fn
                               (medley/find-first
                                #(= (:column-key %) k)
                                (get-in table [:columns :data])))
                              identity)
                row-v (render-fn (get row-data-map k))]
            (if (string? v)
              (some-> row-v
                      str/lower-case
                      (str/includes? v))
              ;; to filter when we have a select tag.
              ;; the v values are in a set
              (get v row-v))))
        column-filters)) rows)
    rows))

(defn resolve-search-all
  [table rows]
  (if-let [search-value (process-string-for-filtering (search-all-value table))]
    (filter
     (fn [row-data]
       (some
        (fn [[k cell-data]]
          (let [render-fn (or (:render-fn
                               (medley/find-first
                                #(= (:column-key %) k)
                                (get-in table [:columns :data])))
                              identity)
                cell (render-fn cell-data)]
            (some-> cell
                    str
                    str/lower-case
                    (str/includes? search-value))))
        row-data)) rows)
    rows))

(defn resolve-hidden-columns
  [table rows]
  (if-let [hidden-columns (seq (get-in table [:columns :hidden]))]
    (map
     (fn [row-data]
       (apply dissoc row-data hidden-columns))
     rows)
    rows))

(defn resolve-pagination
  [table rows]
  (let [current-page (pagination-current-page table)
        rows-per-page (pagination-rows-per-page table)]
    [rows (when (seq rows)
            (nth (partition-all rows-per-page rows)
                 current-page))]))

(defn process-rows
  [table-atom]
  ;; all data transformation is performed here, on READ!
  ;; swap! is not allowed in this function
  (let [table @table-atom
        rows (get-in table [:rows :data])]
    (->> rows
         (resolve-hidden-columns table)
         (resolve-sorting table)
         (resolve-column-filtering table)
         (resolve-search-all table)
         (resolve-pagination table))))

