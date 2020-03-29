(ns reagent-datatable.table-utils
  (:require
   [clojure.string :as s]
   [reagent.core :as r]))

(def example-data
  {;; user provided data
   :columns [{:column-key :status
              :column-name "Status"}
             {:column-key :scale-id
              :column-name "ScaleID"
              :render-fn (fn [row k v])
              :render-only #{:filter :sort :custom-one?}}
             {:column-key :name
              :column-name "Name"}
             {:column-key :location
              :column-name "Location"}
             {:column-key :error
              :column-name "Error"}]
   :rows [{:id (random-uuid)
           :status "ok"
           :scale-id "ASD"
           :name "luch"
           :location "London"
           :error "Overload"}
          {:id (random-uuid)
           :status "something"
           :scale-id "ASD"
           :name "luch"
           :location "London"
           :error "Overload"}]
   :filters {:input #{:scale-id :name}
             :select #{:status :error}}
   ;; utils
   :utils {:theme {:columns :dark
                   :navigation :dark
                   :rows :dark
                   :top :dark}
           :toggle-all-themes true
           :filter-all "value"
           :filter-columns {:status #{["a" "a"]
                                      ["b" [:i.fas.fa-user "component"]]}
                            :scale-id "id"}
           :hidden {:status true
                    :name false}
           :pagination {:rows-per-page 34
                        :current-page 3}
           :sort {:status :asc
                  ;; or :desc
                  }}})

(defn component-hide-show
  [component & [args]]
  (let [!ref-toggle (atom nil)
        !ref-box (atom nil)
        active? (r/atom false)
        handler (fn [e]
                  (let [^js node (.-target e)]
                    (cond
                      ;; don't close box if click happens on child-box
                      (.contains @!ref-box node) nil
                      ;; to toggle box - show/hide
                      (.contains @!ref-toggle node) (swap! active? not)
                      ;; always close child-box when clicking out
                      :else (reset! active? false))))
        ref-toggle (fn [el] (reset! !ref-toggle el))
        ref-box (fn [el] (reset! !ref-box el))]
    (r/create-class
     {:component-did-mount
      (fn []
        (js/document.addEventListener "mouseup" handler))
      :component-will-unmount
      (fn []
        (js/document.removeEventListener "mouseup" handler))
      :reagent-render
      (fn [component]
        [component @active? ref-toggle ref-box args])})))

(defn process-string
  [s]
  (some-> s
          not-empty
          s/trim
          s/lower-case
          (s/replace #"\s+" " ")))

(defn dark-mode?
  [table k]
  (= :dark (get-in table [:utils :theme k])))

(defn set-dark-mode
  [table-atom k]
  (swap! table-atom update-in [:utils :theme k]
         (fn [mode]
           (if (= :dark mode)
             :light
             :dark))))

(defn dark-mode-toggled-all?
  [table]
  (get-in table [:utils :toggle-all-themes]))

(defn set-dark-mode-all
  [table-atom]
  (swap! table-atom
         #(-> %
              (assoc-in [:utils :theme]
                        (let [m (fn [v]
                                  {:top v
                                   :columns v
                                   :rows v
                                   :navigation v})]
                          (if (-> % :utils :toggle-all-themes)
                            (m :light)
                            (m :dark))))
              (update-in [:utils :toggle-all-themes] not))))

(defn reset-pagination
  [table]
  (assoc-in table [:utils :pagination :current-page] 0))

(defn column-sort
  [table-atom column-key]
  (swap! table-atom
         #(-> %
              reset-pagination
              (update-in [:utils :sort]
                         (fn [m]
                           (let [curr-column-key (ffirst m)
                                 curr-sort-val (first (vals m))]
                             (if (= curr-column-key column-key)
                               (update m column-key (fn [order]
                                                      (if (= :asc order)
                                                        :desc :asc)))
                               {column-key :asc})))))))

(defn column-sort-icon
  [table column-key]
  (let [sort (get-in table [:utils :sort])]
    (case (get sort column-key)
      :asc "fa-caret-down"
      :desc "fa-caret-up"
      "fa-caret-down")))

(defn column-sort-value
  [table]
  (-> table :utils :sort))

(defn column-filter-value
  [table column-key]
  (-> table :utils :filter-columns column-key))

(defn column-filter-on-change
  [evt table-atom column-key]
  (swap! table-atom
         #(-> %
              reset-pagination
              (assoc-in [:utils :filter-columns column-key]
                        (-> evt .-target .-value)))))

(defn column-filter-reset
  [table-atom column-key]
  (swap! table-atom update-in [:utils :filter-columns] dissoc column-key))

;; use case statement for this in UI
(defn column-filter-type
  [table column-key]
  (let [input-filters (get-in table [:filters :input])
        select-filters (get-in table [:filters :select])]
    (cond
      (get input-filters column-key) :input
      (get select-filters column-key) :select
      :else nil)))

(defn render-fn
  [table column-key]
  (some->> (:columns table)
           (filter #(= column-key (:column-key %)))
           first
           :render-fn))

(defn process-cell-value
  ([table row column-key value]
   (process-cell-value table row column-key value true))
  ([table row column-key value allow?]
   (let [render-fn (render-fn table column-key)]
     (if (and allow? render-fn)
       (render-fn row column-key value)
       value))))

(defn column-select-filter-options
  [table column-key]
  (let [processed-val #(process-cell-value table % column-key (column-key %))]
    (->> (:rows table)
         ;; to return only relevant k-v pair from row
         (mapv (fn [row]
                 [column-key (column-key row) (processed-val row)]))
         (group-by (juxt first second last))
         ;; to keep only [k raw-v processed-v]
         (map first))))

(defn column-select-filter-on-change
  [table-atom column-key value processed-value]
  (swap! table-atom
         #(-> %
              reset-pagination
              (update-in [:utils :filter-columns column-key]
                         (fn [selected-values]
                           (let [val&processed [value processed-value]]
                             (if (get selected-values val&processed)
                               (disj selected-values val&processed)
                               (conj ((fnil conj #{}) selected-values)
                                     val&processed))))))))

(defn column-select-filter-value
  [table column-key value processed-value]
  (get-in table [:utils :filter-columns column-key [value processed-value] 0] false))

(defn column-select-filter-reset
  [table-atom column-key value]
  (swap! table-atom update-in [:utils :filter-columns column-key]
         (fn [column-filters]
           (->> column-filters
                (remove (fn [v]
                          (and (vector? v)
                               (= (first v) value))))
                (into #{})))))

(defn column-filter-reset-all
  [table-atom]
  (swap! table-atom
         #(-> %
              reset-pagination
              (update :utils dissoc :filter-columns))))

(defn filter-all-value
  [table]
  (get-in table [:utils :filter-all] ""))

(defn filter-all-on-change
  [evt table-atom]
  (swap! table-atom
         #(-> %
              reset-pagination
              (assoc-in [:utils :filter-all]
                        (-> evt .-target .-value)))))

(defn filter-all-reset
  [table-atom]
  (swap! table-atom
         #(-> %
              reset-pagination
              (update :utils dissoc :filter-all))))

(defn block-filter-values
  "Collect only the active column filters for UI"
  [table]
  (->> (get-in table [:utils :filter-columns])
       (remove (comp empty? second))
       ;; from [:a "filter" :k #{"a" "b"}]
       ;; to [[:a "filter"] [:k "a" :select] [:k "b" :select]]
       (map (fn [[k v]]
              (if (set? v)
                (mapv #(vector k % :select) v)
                (vector [k v]))))
       (apply concat)
       (not-empty)))

(defn column-visible?
  [table column-key]
  (not (-> table :utils :hidden column-key)))

(defn column-visibility-on-change
  [table-atom column-key]
  (swap! table-atom
         #(-> %
              reset-pagination
              (update-in [:utils :filter-columns] dissoc column-key)
              (update-in [:utils :hidden column-key] not))))

(defn- hidden-columns
  "Transform hidden column keys from map to vec"
  [table]
  (->> (-> table :utils :hidden)
       (filter second)
       (map first)
       (not-empty)))

(defn table-columns
  [table]
  (let [columns (:columns table)
        hidden (-> table :utils :hidden)]
    (remove #(get hidden (:column-key %)) columns)))

(defn loading?
  [table]
  (empty? (:rows table)))

(defn pagination-rows-per-page-on-change
  [evt table-atom]
  (swap! table-atom
         #(-> %
              (assoc-in [:utils :pagination :rows-per-page]
                        (js/parseInt (-> evt .-target .-value)))
              (assoc-in [:utils :pagination :current-page] 0))))

(defn pagination-rows-per-page
  [table]
  (get-in table [:utils :pagination :rows-per-page] 15))

(defn pagination-current-page
  [table]
  (get-in table [:utils :pagination :current-page] 0))

(defn pagination-current-and-total-pages
  [table processed-rows]
  (let [offset (pagination-current-page table)
        rows-per-page (pagination-rows-per-page table)
        nth-rows-at-page (+ rows-per-page
                            (* offset rows-per-page))
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
  (when-not (pagination-rows-exhausted? @table-atom
                                        processed-rows)
    (swap! table-atom update-in [:utils :pagination :current-page]
           (fnil inc 0))))

(defn pagination-dec-page
  [table-atom]
  (when (> (pagination-current-page @table-atom) 0)
    (swap! table-atom update-in [:utils :pagination :current-page]
           dec)))

(defn render-fn-allow?
  [table column-key operation]
  (let [column-map (first (filter #(= column-key (:column-key %))
                                  (:columns table)))
        deny? (-> column-map :render-only operation)]
    (not deny?)))

(defn date?
  [d]
  (instance? js/Date d))

(defn date-as-sortable
  [d]
  (.getTime d))

(defn compare-vals
  [x y]
  (cond
    (or (and (number? x) (number? y))
        (and (string? x) (string? y))
        (and (boolean? x) (boolean? y)))
    (compare x y)

    (and (date? x) (date? y))
    (compare (date-as-sortable x) (date-as-sortable y))

    :else
    (compare (str x) (str y))))

(defn resolve-sorting
  [table rows]
  (if-let [m (column-sort-value table)]
    (let [column-key (ffirst m)
          order (get m column-key)
          allow-sort? (render-fn-allow? table column-key :sort)
          processed-val (fn [row]
                          (process-cell-value table row column-key
                                              (column-key row)
                                              allow-sort?))]
      (sort
       (fn [row1 row2]
         (let [val1 (processed-val row1)
               val2 (processed-val row2)]

           (if (= :desc order)
             (compare-vals val2 val1)
             (compare-vals val1 val2))))
       rows))
    rows))

(defn column-filters
  [table]
  (->> (get-in table [:utils :filter-columns])
       (remove (fn [k-v] (empty? (second k-v))))
       (map (fn [[k v]] [k (if (string? v)
                             (process-string v)
                             v)]))
       (not-empty)))

(defn resolve-column-filtering
  [table rows]
  (if-let [column-filters (column-filters table)]
    (filter
     (fn [row]
       (every?
        (fn [[column-key filtering]]
          (let [allow-filter? (render-fn-allow? table column-key :filter)
                processed-val (str (process-cell-value table row column-key
                                                       (column-key row)
                                                       allow-filter?))]
            (if (string? filtering)
              (s/includes? (s/lower-case processed-val) filtering)
              (get (->> filtering
                        (map
                         (comp
                          str (if allow-filter? second first)))
                        (into #{}))
                   processed-val))))
        column-filters))
     rows)
    rows))

(defn resolve-filter-all
  [table rows]
  (if-let [filter-value (process-string
                         (filter-all-value table))]
    (filter
     (fn [row]
       (some
        (fn [[column-key cell-value]]
          (let [allow-filter? (render-fn-allow? table column-key :filter)
                processed-val (str (process-cell-value table row column-key
                                                       cell-value
                                                       allow-filter?))]
            (s/includes?
             (s/lower-case processed-val)
             filter-value)))
        (dissoc row :id)))
     rows)
    rows))

(defn resolve-hidden-columns
  [table rows]
  (if-let [columns-to-hide (hidden-columns table)]
    (map
     (fn [row]
       (apply dissoc row columns-to-hide))
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
  [table]
  (let [rows (-> table :rows)]
    (->> rows
         (resolve-hidden-columns table)
         (resolve-sorting table)
         (resolve-column-filtering table)
         (resolve-filter-all table)
         (resolve-pagination table))))
