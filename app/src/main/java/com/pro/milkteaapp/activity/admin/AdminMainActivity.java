package com.pro.milkteaapp.activity.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.pro.milkteaapp.R;
import com.pro.milkteaapp.SessionManager;
import com.pro.milkteaapp.activity.LoginActivity;
import com.pro.milkteaapp.activity.MainActivity;
import com.pro.milkteaapp.databinding.ActivityAdminMainBinding;
import com.pro.milkteaapp.fragment.admin.AdminOrdersFragment;
import com.pro.milkteaapp.fragment.admin.AdminStatisticsFragment;
import com.pro.milkteaapp.fragment.admin.AdminUsersFragment;
import com.pro.milkteaapp.fragment.admin.management.ManagementFragment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AdminMainActivity extends AppCompatActivity {

    private ActivityAdminMainBinding binding;

    private static final long NAV_DEBOUNCE_MS = 400L;
    private long lastNavClick = 0L;

    public static final String EXTRA_ADMIN_TARGET = "admin_tab"; // orders, users, stats, manage

    private static final String STATE_SELECTED_TAB = "state_selected_tab";
    private @IdRes int currentTabId = R.id.adminManagement;

    private static final String TAG_ORDERS     = "tab_orders";
    private static final String TAG_USERS      = "tab_users";
    private static final String TAG_STATISTICS = "tab_statistics";
    private static final String TAG_MANAGEMENT = "tab_management";

    private final Map<Integer, String> idToTag = new HashMap<>();
    private final Map<String, Fragment> tagToFragment = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            goToLoginRoot(); return;
        }

        binding = ActivityAdminMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        idToTag.put(R.id.adminOrders,     TAG_ORDERS);
        idToTag.put(R.id.adminUsers,      TAG_USERS);
        idToTag.put(R.id.adminStatistics, TAG_STATISTICS);
        idToTag.put(R.id.adminManagement, TAG_MANAGEMENT);

        setupToolbar();
        setupBottomNavigation();

        SessionManager sm = new SessionManager(this);
        if (sm.hasCachedRole()) {
            if (!sm.isAdmin()) { Toast.makeText(this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show(); goToUserRoot(); return; }
            continueInit(savedInstanceState);
        } else {
            fetchRoleThenInit(sm, savedInstanceState);
        }
    }

    private void continueInit(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt(STATE_SELECTED_TAB, R.id.adminManagement);
        }
        restoreOrCreateFragments();

        if (savedInstanceState == null) {
            if (!handleIntent(getIntent())) {
                binding.bottomNavigationView.setSelectedItemId(R.id.adminManagement);
                switchTo(R.id.adminManagement);
            }
        } else {
            binding.bottomNavigationView.setSelectedItemId(currentTabId);
            switchTo(currentTabId);
        }
    }

    private void fetchRoleThenInit(SessionManager sm, @Nullable Bundle savedInstanceState) {
        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = null;
                    if (doc != null) {
                        Object r = doc.get("role");
                        if (r instanceof String) role = ((String) r).trim();
                        else {
                            Object isAdmin = doc.get("isAdmin");
                            if (isAdmin instanceof Boolean && (Boolean) isAdmin) role = "admin";
                        }
                    }
                    sm.setRole(role);
                    if (!sm.isAdmin()) { Toast.makeText(this, "Bạn không có quyền truy cập (admin-only)", Toast.LENGTH_SHORT).show(); goToUserRoot(); return; }
                    continueInit(savedInstanceState);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Không kiểm tra được quyền: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    goToUserRoot();
                });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, currentTabId);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private boolean handleIntent(Intent intent) {
        if (intent == null) return false;
        String target = intent.getStringExtra(EXTRA_ADMIN_TARGET);
        if (target == null) return false;

        @IdRes int menuId = mapTargetToMenuId(target);
        if (menuId == 0) return false;

        if (binding.bottomNavigationView.getSelectedItemId() == menuId) {
            switchTo(menuId);
        } else {
            binding.bottomNavigationView.setSelectedItemId(menuId);
        }
        return true;
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        updateToolbarTitle(currentTabId);
        binding.toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);
    }

    private void setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener(onNavSelectedListener);
        binding.bottomNavigationView.setOnItemReselectedListener(item -> { /* optional scroll-to-top */ });
    }

    private final NavigationBarView.OnItemSelectedListener onNavSelectedListener = item -> {
        long now = SystemClock.elapsedRealtime();
        if (now - lastNavClick < NAV_DEBOUNCE_MS) return false;
        lastNavClick = now;
        return switchTo(item.getItemId());
    };

    private void restoreOrCreateFragments() {
        Fragment orders     = getSupportFragmentManager().findFragmentByTag(TAG_ORDERS);
        Fragment users      = getSupportFragmentManager().findFragmentByTag(TAG_USERS);
        Fragment statistics = getSupportFragmentManager().findFragmentByTag(TAG_STATISTICS);
        Fragment management = getSupportFragmentManager().findFragmentByTag(TAG_MANAGEMENT);

        if (orders == null)     orders     = new AdminOrdersFragment();
        if (users == null)      users      = new AdminUsersFragment();
        if (statistics == null) statistics = new AdminStatisticsFragment();
        if (management == null) management = new ManagementFragment();

        tagToFragment.put(TAG_ORDERS, orders);
        tagToFragment.put(TAG_USERS, users);
        tagToFragment.put(TAG_STATISTICS, statistics);
        tagToFragment.put(TAG_MANAGEMENT, management);

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (!orders.isAdded())     ft.add(R.id.fragment_container, orders, TAG_ORDERS).hide(orders);
        if (!users.isAdded())      ft.add(R.id.fragment_container, users, TAG_USERS).hide(users);
        if (!statistics.isAdded()) ft.add(R.id.fragment_container, statistics, TAG_STATISTICS).hide(statistics);
        if (!management.isAdded()) ft.add(R.id.fragment_container, management, TAG_MANAGEMENT).hide(management);
        ft.commitNowAllowingStateLoss();
    }

    private boolean switchTo(@IdRes int menuId) {
        String targetTag = idToTag.get(menuId);
        if (targetTag == null) return false;

        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();

        for (Map.Entry<String, Fragment> e : tagToFragment.entrySet()) {
            Fragment f = e.getValue();
            if (f != null && f.isAdded()) ft.hide(f);
        }

        Fragment target = tagToFragment.get(targetTag);
        if (target == null) return false;
        ft.show(target);
        ft.commitAllowingStateLoss();

        currentTabId = menuId;
        updateToolbarTitle(menuId);
        return true;
    }

    private void updateToolbarTitle(@IdRes int menuId) {
        if (getSupportActionBar() == null) return;
        int titleRes = R.string.app_name;
        if (menuId == R.id.adminOrders)         titleRes = R.string.admin_title_orders;
        else if (menuId == R.id.adminUsers)     titleRes = R.string.admin_title_users;
        else if (menuId == R.id.adminStatistics)titleRes = R.string.admin_title_statistics;
        else if (menuId == R.id.adminManagement)titleRes = R.string.admin_title_management;
        getSupportActionBar().setTitle(titleRes);
    }

    @Override
    public void onBackPressed() {
        Fragment current = tagToFragment.get(idToTag.get(currentTabId));
        if (current != null && current.getChildFragmentManager().popBackStackImmediate()) {
            return;
        }
        if (currentTabId != R.id.adminManagement) {
            binding.bottomNavigationView.setSelectedItemId(R.id.adminManagement);
            switchTo(R.id.adminManagement);
        } else {
            super.onBackPressed();
        }
    }

    private void goToUserRoot() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void goToLoginRoot() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin_toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) { logout(); return true; }
        else if (id == R.id.action_switch_to_user) { goToUserRoot(); return true; }
        else if (id == R.id.action_refresh) {
            Fragment f = tagToFragment.get(idToTag.get(currentTabId));
            if (f instanceof SupportsRefresh) ((SupportsRefresh) f).refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logout() {
        try { FirebaseAuth.getInstance().signOut(); } catch (Throwable ignored) {}
        new SessionManager(this).clear();
        goToLoginRoot();
    }

    @IdRes
    private int mapTargetToMenuId(@NonNull String target) {
        return switch (target) {
            case "orders" -> R.id.adminOrders;
            case "users" -> R.id.adminUsers;
            case "stats", "statistics" -> R.id.adminStatistics;
            case "manage", "management" -> R.id.adminManagement;
            default -> 0;
        };
    }

    public interface ScrollToTop { void scrollToTop(); }
    public interface SupportsRefresh { void refresh(); }
}
