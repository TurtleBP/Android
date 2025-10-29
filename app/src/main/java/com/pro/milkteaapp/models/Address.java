package com.pro.milkteaapp.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.Date;

public class Address implements Serializable, Parcelable {

    private String id;
    private String fullName;
    private String phone;
    private String line1;
    private String line2;
    private String city;       // Quận/Huyện
    private String province;   // Tỉnh/Thành phố
    private String postalCode;
    private boolean isDefault;

    @ServerTimestamp
    private Date createdAt;

    public Address() {}

    protected Address(Parcel in) {
        id = in.readString();
        fullName = in.readString();
        phone = in.readString();
        line1 = in.readString();
        line2 = in.readString();
        city = in.readString();
        province = in.readString();
        postalCode = in.readString();
        isDefault = in.readByte() != 0;
        long t = in.readLong();
        createdAt = t == -1 ? null : new Date(t);
    }

    public static final Creator<Address> CREATOR = new Creator<Address>() {
        @Override public Address createFromParcel(Parcel in) { return new Address(in); }
        @Override public Address[] newArray(int size) { return new Address[size]; }
    };

    @NonNull
    public String displayLine() {
        StringBuilder sb = new StringBuilder();
        append(sb, line1);
        append(sb, line2);
        append(sb, city);
        append(sb, province);
        if (postalCode != null && !postalCode.trim().isEmpty()) append(sb, "Mã " + postalCode.trim());
        return sb.toString();
    }

    private static void append(StringBuilder sb, @Nullable String part) {
        if (part == null) return;
        String p = part.trim();
        if (p.isEmpty()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(p);
    }

    // getters/setters
    @Nullable public String getId() { return id; }
    public void setId(@Nullable String id) { this.id = id; }

    @Nullable public String getFullName() { return fullName; }
    public void setFullName(@Nullable String fullName) { this.fullName = fullName; }

    @Nullable public String getPhone() { return phone; }
    public void setPhone(@Nullable String phone) { this.phone = phone; }

    @Nullable public String getLine1() { return line1; }
    public void setLine1(@Nullable String line1) { this.line1 = line1; }

    @Nullable public String getLine2() { return line2; }
    public void setLine2(@Nullable String line2) { this.line2 = line2; }

    @Nullable public String getCity() { return city; }
    public void setCity(@Nullable String city) { this.city = city; }

    @Nullable public String getProvince() { return province; }
    public void setProvince(@Nullable String province) { this.province = province; }

    @Nullable public String getPostalCode() { return postalCode; }
    public void setPostalCode(@Nullable String postalCode) { this.postalCode = postalCode; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    @Nullable public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(@Nullable Date createdAt) { this.createdAt = createdAt; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(fullName);
        dest.writeString(phone);
        dest.writeString(line1);
        dest.writeString(line2);
        dest.writeString(city);
        dest.writeString(province);
        dest.writeString(postalCode);
        dest.writeByte((byte) (isDefault ? 1 : 0));
        dest.writeLong(createdAt == null ? -1 : createdAt.getTime());
    }
}
