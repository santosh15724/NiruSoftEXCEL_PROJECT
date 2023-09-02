package com.niruSoft.niruSoft.model;

import lombok.*;

import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubObjectData {
    private String TOTAL;
    private String Coolie;
    private List<String> C;
    private List<String> UNIT;
    private List<String> Rate;
    private String Amount;
    private List<String> Item_qty;
    private String SC;
    private String Luggage;
    private List<String> CUSTOMER_NAME;
    private String KGSUM; // You might need to change this to match the actual structure
    private List<String> DATE;
    private List<String> ITEM;
    private List<String> SALES_MAN;
    private List<String> B_Cash;
    private List<String> Net_Amt;
    private List<String> QTY;
    private String EXP;
    private List<String> FARMER_NAME;
    
    // Getters and setters for the fields
}
