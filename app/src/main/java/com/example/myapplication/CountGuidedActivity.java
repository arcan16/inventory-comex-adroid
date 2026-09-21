package com.example.myapplication;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class CountGuidedActivity extends AppCompatActivity {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_PRESENTATION = "extra_presentation";
    public static final String EXTRA_DATE = "extra_date";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_count_guided);
    }
}
