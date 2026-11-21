package com.example.milk_khata.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import java.util.List;

@Dao
public interface SlipDao {

    @Insert
    void insert(Slip slip);

    // We will use this later for the Dashboard
    @Query("SELECT * FROM slip_table ORDER BY id DESC")
    List<Slip> getAllSlips();

    // Deletes everything (useful for resetting/testing)
    @Query("DELETE FROM slip_table")
    void deleteAllSlips();

    @Delete
    void delete(Slip slip); // Add this line
}