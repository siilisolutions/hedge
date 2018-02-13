(ns hedge.common)

(defn outputs->atoms
    "Receives the outputs as a vector defined in hedge.edn and gives out a map of atoms"
    [outputs]
    (into {} (map (fn [output] {(-> output :key keyword) {:key (-> output :key) 
                                                          :value (atom nil)
                                                          :type (-> output :type)}}) 
                  outputs)))