package com.example.gameset1;

// Simple Data Model for the Android Client
public class Card {
    public final int id;
    public final int number;
    public final int shape;
    public final int shading;
    public final int color;

    // We don't need Thread logic here on the client side
    // The server handles the threads. We just display the data.
    public Card(int number, int shape, int shading, int color, Object unused) {
        this.number = number;
        this.shape = shape;
        this.shading = shading;
        this.color = color;
        this.id = number * 27 + shape * 9 + shading * 3 + color;
    }
}