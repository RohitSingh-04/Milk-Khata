package com.example.milk_khata.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "slip_table")
public class Slip {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String date;
    public double qty;
    public double rate;
    public double amount;
    public double incentive;
    public String imagePath; // Added to store the private internal path

    public Slip(String date, double qty, double rate, double amount, double incentive, String imagePath) {
        this.date = date;
        this.qty = qty;
        this.rate = rate;
        this.amount = amount;
        this.incentive = incentive;
        this.imagePath = imagePath;
    }
}