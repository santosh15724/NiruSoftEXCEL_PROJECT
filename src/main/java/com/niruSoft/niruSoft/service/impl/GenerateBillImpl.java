package com.niruSoft.niruSoft.service.impl;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface GenerateBillImpl {

//    public Map<String, Map<String, Map<String, List<String>>>> processExcelData(InputStream inputStream);
//    public Map<String, Map<String, List<String>>> processExcelData(InputStream inputStream);
//public Object validateServices(InputStream inputStream);
public JSONObject processExcelData(InputStream inputStream);
}
