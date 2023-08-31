package com.niruSoft.niruSoft.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedFarmerData {
    private Map<String, ItemCategory> data;

    public Map<String, ItemCategory> getData() {
        return data;
    }

    public static class ItemCategory {
        private List<String> ITEM;
        private List<Integer> itemQty;

        public List<String> getITEM() {
            return ITEM;
        }

        public List<Integer> getItemQty() {
            return itemQty;
        }
    }
}
