package com.pro.milkteaapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CheckoutInfo {
    @NonNull public final String address;        // chuỗi gộp
    @Nullable public final Address addressObj;   // object đầy đủ
    @NonNull public final String paymentMethod;
    @NonNull public final String voucherCode;
    @NonNull public final String shippingLabel;
    public final double subtotal;
    public final double discount;
    public final double shippingFee;
    public final double grandTotal;

    public CheckoutInfo(@NonNull String address,
                        @Nullable Address addressObj,
                        @NonNull String paymentMethod,
                        @NonNull String voucherCode,
                        @NonNull String shippingLabel,
                        double subtotal, double discount, double shippingFee, double grandTotal) {
        this.address = address;
        this.addressObj = addressObj;
        this.paymentMethod = paymentMethod;
        this.voucherCode = voucherCode;
        this.shippingLabel = shippingLabel;
        this.subtotal = subtotal;
        this.discount = discount;
        this.shippingFee = shippingFee;
        this.grandTotal = grandTotal;
    }

    @NonNull public String getAddress() { return address; }
    @Nullable public Address getAddressObj() { return addressObj; }
    @NonNull public String getPaymentMethod() { return paymentMethod; }
    @NonNull public String getVoucherCode() { return voucherCode; }
    @NonNull public String getShippingLabel() { return shippingLabel; }
    public double getSubtotal() { return subtotal; }
    public double getDiscount() { return discount; }
    public double getShippingFee() { return shippingFee; }

    public double getGrandTotal() {
        return 0;
    }
}
