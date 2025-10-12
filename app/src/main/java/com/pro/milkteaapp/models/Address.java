package com.pro.milkteaapp.models;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Address {
    private String id;
    private String fullName;
    private String phone;
    private String line1;
    private String line2; // <-- thêm dòng này
    private String district;
    private String city;
    private String note;
    private boolean isDefault;
    @ServerTimestamp
    private Date createdAt;

    public Address() {}

    // Getter & Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }

    public String getLine2() { return line2; }   // <-- thêm getter
    public void setLine2(String line2) { this.line2 = line2; }  // <-- thêm setter

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
