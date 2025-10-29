package com.pro.milkteaapp.repository;

import androidx.lifecycle.LiveData;

import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.utils.Result;

import java.util.List;
import java.util.Map;

/**
 * Repository cho collection "categories" trên Firestore.
 * Triển khai tại: service/CategoryRepositoryImpl.java
 */
public interface CategoryRepository {
    /** Dùng cho user & dropdown chọn danh mục (chỉ các mục đang active). */
    LiveData<Result<List<Category>>> listenAllActive();

    /** Dùng cho màn admin (tất cả danh mục, sort theo createdAt desc trong Impl). */
    LiveData<Result<List<Category>>> listenAll();

    /** Lấy 1 danh mục theo id. */
    LiveData<Result<Category>> getById(String id);

    /** Thêm mới (Map fields: name, imageUrl, active, description, ...). */
    LiveData<Result<Void>> add(Map<String, Object> data);

    /** Cập nhật theo id (Map fields, Impl sẽ set updatedAt = serverTimestamp). */
    LiveData<Result<Void>> update(String id, Map<String, Object> data);

    /** Xoá theo id. */
    LiveData<Result<Void>> delete(String id);
}
