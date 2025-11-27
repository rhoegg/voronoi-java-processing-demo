package com.ryanhoegg.voronoi.sandbox;

public interface Visualization {
    void reset();
    void draw();
    void step();
    void update(float dt); // seconds
    void keyPressed(char key, int keyCode);
}
// very useful for getting intuitive understanding of the vector math
// https://mathinsight.org/vector_introduction