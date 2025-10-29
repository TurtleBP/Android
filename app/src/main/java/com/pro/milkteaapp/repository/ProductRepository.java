package com.pro.milkteaapp.repository;

import androidx.lifecycle.LiveData;

import com.pro.milkteaapp.models.Products;
import com.pro.milkteaapp.utils.Result;

import java.util.List;
import java.util.Map;

public interface ProductRepository {

    // Realtime toàn bộ sản phẩm
    LiveData<Result<List<Products>>> listenAll();

    // Lấy 1 sản phẩm theo id (one-shot fetch)
    LiveData<Result<Products>> getById(String id);

    // CRUD
    LiveData<Result<Void>> add(Map<String, Object> data);
    LiveData<Result<Void>> update(String id, Map<String, Object> data);
    LiveData<Result<Void>> delete(String id);
}
