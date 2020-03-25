(ns reagent-datatable.table
  (:require [reagent-datatable.table-utils :as utils]
            [reagent.core :as r]))

(defn loading-table-body
  "Renders a :tbody element containing a placeholder animation for use when a
  tables content will be filled sometime after the page actually loads, likely
  due to an ajax call"
  [{:keys [rows cols]}]
  (into [:tbody]
        (for [row (range rows)]
          ^{:key row}
          (into [:tr.loading]
                (for [col (range cols)]
                  ^{:key col}
                  [:td.td-loading-bar
                   [:span]])))))

(defn component-hide-show
  [_component & [args]]
  (let [!ref-toggle (atom nil)
        !ref-box (atom nil)
        active? (r/atom false)
        ref-toggle (fn [el] (reset! !ref-toggle el))
        ref-box (fn [el] (reset! !ref-box el))]
    (r/create-class
     (let [handler (fn [e]
                     (let [^js node (.-target e)]
                       (cond
                         ;; don't close box if click happens on child-box
                         (.contains @!ref-box node) nil
                         ;; to toggle box - show/hide
                         (.contains @!ref-toggle node) (swap! active? not)
                         ;; always close child-box when clicking out
                         :else (reset! active? false))))]
       {:component-did-mount
        (fn []
          (js/document.addEventListener "mouseup" handler))
        :component-will-unmount
        (fn []
          (js/document.removeEventListener "mouseup" handler))
        :reagent-render
        (fn [component]
          [component @active? ref-toggle ref-box args])}))))

(defn column-filter-select
  [table-atom column-key]
  [:div.table-column__filter
   [component-hide-show
    (fn [active? ref-toggle ref-box]
      [:div.table-column__button-wrapper
       [:button.button.table-column__button
        {:ref ref-toggle}
        [:i.table-column__button-icon.fa.fa-filter]
        [:span "Select"]
        [:i.table-column__button-icon.fa.fa-chevron-down]]
       [:div.table-column__button-options
        {:class (when active? "table-column__button-options--show")
         :ref ref-box}
        (doall
         (for [value (utils/column-select-filter-options @table-atom column-key)]
           ^{:key value}
           [:div.action__checkbox
            {:on-click #(utils/column-select-filter-on-change table-atom value column-key)}
            [:input.checkbox__input
             {:type "checkbox"
              :checked (utils/column-select-filter-value @table-atom column-key value)
              :value value
              :on-change #()}]
            [:label.checkbox__custom
             value]]))]])]])

(defn column-filter-input
  [table-atom column-key]
  [:div.table-column__filter
   [:input.input.input--side-icons.input--no-borders
    {:value (utils/column-filter-value @table-atom column-key)
     :on-change #(utils/column-filter-on-change % table-atom column-key)}]
   [:span.input__icon.input__left-icon
    [:i.fa.fa-filter]]
   (when (not-empty (utils/column-filter-value @table-atom column-key))
     [:span.input__icon.input__right-icon.input__icon--clickable
      {:on-click #(utils/column-filter-reset table-atom column-key)}
      [:i.fa.fa-times]])])

(defn header-columns
  [table-atom]
  (let [columns (utils/table-columns @table-atom)]
    [:thead.table__head
     (into [:tr]
           (mapv
            (fn [{:keys [column-key column-name]}]
              ^{:key column-key}
              [:th.table__cell.head__cell
               [:div.head__column-title
                {:on-click #(utils/column-sort table-atom column-key)}
                [:span column-name]
                [:i.fa.fa-sort.column-title__sort-icon]]
               (when (utils/column-filters? @table-atom column-key)
                 (if (utils/column-select-input? @table-atom column-key)
                   [column-filter-select table-atom column-key]
                   [column-filter-input table-atom column-key]))])
            columns))]))

(defn body-rows
  [table rows]
  (let [columns (utils/table-columns table)
        link-row-fn (get-in table [:rows :link-row-fn])]
    (if (:loading? table)
      [loading-table-body {:rows 4 :cols (count columns)}]
      [:tbody.table__body
       (for [row rows]
         ^{:key row}
         [:tr.table__row.body__row
          (for [{:keys [column-key render-fn class]
                 :or {render-fn identity
                      class ""}} columns]
            ^{:key (str row column-key)}
            [:td.table__cell.body__cell
             {:class class}
             [:a
              {:href (when link-row-fn (link-row-fn row))}
              (render-fn (column-key row))]])])])))

(defn search-all
  [table-atom]
  [:div.top__search-all
   [:input.input.input--side-icons.input--no-borders
    {:value (utils/search-all-value @table-atom)
     :on-change #(utils/search-all-on-change % table-atom)}]
   [:span.input__icon.input__left-icon
    [:i.fa.fa-search]]
   (when (not-empty (utils/search-all-value @table-atom))
     [:span.input__icon.input__right-icon.input__icon--clickable
      {:on-click #(utils/search-all-reset table-atom)}
      [:i.fa.fa-times]])])

(defn actions
  [table-atom]
  [:div.top__actions
   [:div.action
    [:i.fa.fa-refresh
     {:on-click (:refresh-action @table-atom)}]]
   [component-hide-show
    (fn [active? ref-toggle ref-box]
      [:div.action
       [:i.action__icon.fa.fa-th-large
        {:ref ref-toggle}]
       (into
        [:div.action__options
         {:class (when active? "action__options--show")
          :ref ref-box}
         [:div
          {:style {:color "black"}}
          "Hide Columns"]]
        (for [{:keys [column-key column-name]}
              (-> @table-atom :columns :data)]
          ^{:key column-key}
          [:div.action__checkbox
           {:on-click #(utils/column-visibility-on-change table-atom column-key)}
           [:input.checkbox__input
            {:type "checkbox"
             :checked (utils/column-visible? @table-atom column-key)}]
           [:label.checkbox__custom
            column-name]]))])]])

(defn active-filters
  [table-atom]
  (let [active-filters (utils/block-filter-values @table-atom)]
    [:div.top__block-filters
     (when (seq active-filters)
       [:button.button--light.button.top__clear-filters
        {:on-click #(utils/column-filter-reset-all table-atom)}
        "RESET"])
     (for [[column-key value] active-filters]
       ^{:key column-key}
       [:button.button.button__active-filters
        {:on-click #(utils/column-filter-reset table-atom column-key value)}
        [:span value]
        [:i.fa.fa-times-circle]])]))

(defn no-data-message
  [rows]
  (when (empty? rows)
    [:div.table__no-data
     [:div "Nothing to show"]]))

(defn table
  "Headers is a vector of columns, each column a map with the following keys:
                         :column-name - a string which will be displayed in the
                                        header of each column
                         :column-key - This should match the key of the row
                                       object you want this column to represent
                         :render-fn (optional) - a function which returns valid
                                                 hiccup syntax to be used for
                                                 each cell of the column

  data is a vector of rows, each row is a map which should contain keys
                    matching those in the columns data structure. Extra keys in
                    each row are ok,they will be ignored and not rendered
                    unless present in the columns

  options is an optional map with overrides to the default configuration of the table.
  Example options are:
     :filters - a list of column keys which should show a dropdown select filter rather
                than a text search in that column
     :loading? - a boolean which when true will cause the table to render a
                 loading animation
     :refresh-action - A function that is called when the refresh icon is
                       clicked. By default clicking the refresh icon simply reloads
                       the page
     :search-query - this value will be autofilled in the global search box,
                     useful for programatically setting the table to filter
                     based on some external value, such as query params in the URL

  Example value of headers:

  {:column-key :foo :column-name 'Foo']

  Example value of data:

  [{:foo 'a value'}
   {:foo 'another value' :bar 'this won't be rendered'}]}} "
  ([headers data] (table headers data {}))
  ([headers data {:keys [filters search-query link-row-fn] :as options}]
   (let [table-atom (r/atom
                     (merge {:head {:search-all (or search-query "")}
                             :refresh-action #(js/window.location.reload)
                             :loading? false
                             :columns
                             {:hidden #{}
                              :filter-select (into {}
                                                   (for [filter filters]
                                                     [filter #{}]))
                              :column-filters (into {}
                                                    (for [filter filters]
                                                      [filter :select]))
                              :data headers}
                             :rows {:data data
                                    :link-row-fn link-row-fn}}
                            options))]
     (fn []
       (let [table @table-atom
             [processed-rows paginated-rows] (utils/process-rows table-atom)]
         [:div.table__wrapper
          [:div.table__top
           [:div.top__first-group
            [search-all table-atom]
            [actions table-atom]]
           [active-filters table-atom]]
          [:div.table__main
           [no-data-message processed-rows]
           [:table.table
            [header-columns table-atom]
            [body-rows table paginated-rows]]]
          [:table.table__foot
           [:tfoot
            [:tr
             [:td.foot__pagination
              [:div.select.pagination__select
               [:select
                {:value (utils/pagination-rows-per-page table)
                 :on-change #(utils/pagination-rows-per-page-on-change % table-atom)}
                [:option {:value "5"} (str "5" " rows")]
                [:option {:value "15"} (str "15" " rows")]
                [:option {:value "100"} (str "100" " rows")]]]
              [:div.pagination__info
               (utils/pagination-current-and-total-pages table processed-rows)]
              [:div.pagination__arrow-group
               [:div.pagination__arrow-nav
                {:class (when (<= (utils/pagination-current-page table) 0)
                          "pagination__arrow-nav--disabled")
                 :on-click #(utils/pagination-dec-page table-atom)}
                [:i.fa.fa-chevron-left]]
               [:div.pagination__arrow-nav
                {:class
                 (when (utils/pagination-rows-exhausted? table processed-rows)
                   "pagination__arrow-nav--disabled")
                 :on-click #(utils/pagination-inc-page table-atom processed-rows)}
                [:i.fa.fa-chevron-right]]]]]]]])))))
