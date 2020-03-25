# reagent-datatable
A reagent table component with pagination, search, filters, sorting etc

This is basically a rewrite of [this existing reagent table](https://github.com/Frozenlock/reagent-table) but with some extra features and hopefully nicer to read code (although thats still a work in progress...)

Current functionality:

- Sortable columns
- Global search bar for searching anything within the table
- Optional search bar for each column
- Optional drop down filter for each column, useful when you have a small number of unique values for each item in the column
- Pagination - client side only for now, but not too hard to adapt it for server side pagination
- Hide/show columns - useful to reduce clutter when working on different tasks
- Fairly responsive - Obviously its not recommended to use tables on small mobile screens, but it will at least function and not break on any screen size

Future functionality:

CRUD actions for each row
Select multiple rows for editing/dispatching custom actions


# How to use

- Add to your deps.edn file:

```
{armincerf/reagent-datatable
  {:git/url "https://github.com/armincerf/reagent-datatable" :sha "32d357c2d04ee7f81b4279e9673e974a6ab8dec7"}}}
```

- Require in your view file:

```
(ns my.view
   (:require [reagent-datatable.table :as table]))
```

- Copy the styles from resources/styles.css and tweak as needed

- Use!

```
(defn my-page
  []
  (let [headers [{:column-key :product-name
                  :column-name "Name"}
                 {:column-key :id
                  :column-name "ID"}
                 {:column-key :status
                  :column-name "Status"}
                 {:column-key :created-at
                  :column-name "Created at"
                  :render-fn #(str "I'm custom! " %)}]
        data [{:product-name "name"
               :id "1"
               :status "active"
               :created-at "2017-01-01T12:00"}]]
    [table/table headers data
     ;;This map and all keys in it are optional
     {:filters [:status]
      :refresh-action #(rf/dispatch [:fetch-data])
      :loading? @(rf/subscribe [:data-loading?])
      :search-query (:filter (query-params))
      :link-row-fn (fn [row] (str "/view/" (:id row)))}]))
```
