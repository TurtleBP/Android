package com.pro.milkteaapp.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.pro.milkteaapp.models.Category;
import com.pro.milkteaapp.repository.CategoryRepository;
import com.pro.milkteaapp.service.CategoryRepositoryImpl;
import com.pro.milkteaapp.utils.Result;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ViewModel trung gian giữa UI và CategoryRepository.
 * - Lắng nghe realtime list
 * - CRUD danh mục
 * - Có thêm helper methods để bớt phải tạo Map ở UI
 */
public class AdminCategoriesViewModel extends ViewModel {
    private final CategoryRepository repo;

    public AdminCategoriesViewModel(CategoryRepository repo) {
        this.repo = repo;
    }

    /* ====== Queries ====== */

    /** Dành cho admin: nghe toàn bộ danh mục (Impl sort theo createdAt desc). */
    public LiveData<Result<List<Category>>> listenAll() {
        return repo.listenAll();
    }

    /** Dành cho user / dropdown: chỉ lấy category active, sort theo name asc (Impl). */
    public LiveData<Result<List<Category>>> listenAllActive() {
        return repo.listenAllActive();
    }

    /** Lấy chi tiết 1 category. */
    public LiveData<Result<Category>> getById(String id) {
        return repo.getById(id);
    }

    /* ====== Commands (Map-based, tương thích Repository) ====== */

    public LiveData<Result<Void>> add(Map<String, Object> data) {
        // đảm bảo searchableName nếu UI không truyền
        if (data != null && data.containsKey("name") && !data.containsKey("searchableName")) {
            Object nameObj = data.get("name");
            if (nameObj instanceof String) {
                data.put("searchableName", ((String) nameObj).toLowerCase(Locale.ROOT));
            }
        }
        return repo.add(data);
    }

    public LiveData<Result<Void>> update(String id, Map<String, Object> data) {
        // đảm bảo searchableName khi đổi tên
        if (data != null && data.containsKey("name") && !data.containsKey("searchableName")) {
            Object nameObj = data.get("name");
            if (nameObj instanceof String) {
                data.put("searchableName", ((String) nameObj).toLowerCase(Locale.ROOT));
            }
        }
        return repo.update(id, data);
    }

    public LiveData<Result<Void>> delete(String id) {
        return repo.delete(id);
    }

    /* ====== Helpers (tiện cho UI, không cần tự tạo Map) ====== */

    /** Thêm nhanh: name + imageUrl + active + (optional) description. */
    public LiveData<Result<Void>> add(String name, String imageUrl, boolean active, String description) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("imageUrl", imageUrl);
        data.put("active", active);
        if (description != null) data.put("description", description);
        data.put("searchableName", name == null ? "" : name.toLowerCase(Locale.ROOT));
        return repo.add(data);
    }

    /** Cập nhật nhanh: các field nào không null sẽ được cập nhật. */
    public LiveData<Result<Void>> update(String id, String name, String imageUrl, Boolean active, String description) {
        Map<String, Object> data = new HashMap<>();
        if (name != null) {
            data.put("name", name);
            data.put("searchableName", name.toLowerCase(Locale.ROOT));
        }
        if (imageUrl != null) data.put("imageUrl", imageUrl);
        if (active != null) data.put("active", active);
        if (description != null) data.put("description", description);
        return repo.update(id, data);
    }

    /** Bật/tắt nhanh trạng thái active. */
    public LiveData<Result<Void>> setActive(String id, boolean active) {
        Map<String, Object> data = new HashMap<>();
        data.put("active", active);
        return repo.update(id, data);
    }

    /* ====== Factory ====== */
    public static class Factory implements ViewModelProvider.Factory {
        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(AdminCategoriesViewModel.class)) {
                return (T) new AdminCategoriesViewModel(new CategoryRepositoryImpl());
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
