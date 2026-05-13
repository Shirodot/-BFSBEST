# BFS-Based Multi-Threshold Image Segmentation

A Java implementation of multi-threshold image segmentation using Breadth-First Search (BFS) and cumulative sum optimization. Developed as part of the Computer Science & Information Engineering curriculum at National Penghu University of Science and Technology.

## 🚀 Overview
This project implements an advanced image segmentation algorithm that finds optimal grey-level thresholds by minimizing within-group variance (similar to Otsu's method, but extended for $k$ thresholds).

### Key Features
* **BFS Search Strategy**: Explores threshold combinations level-by-level.
* **O(1) Statistical Lookups**: Uses precomputed cumulative count, sum, and squared sum arrays to calculate region variance in constant time.
* **Scalable Thresholding**: Supports $k=1, 2, \dots, n$ thresholds.
* **Complexity Optimized**: Reduces the search space through pruning (minimum grey-level gap).

## 🛠 Algorithm Workflow
1.  **Histogram Construction**: $O(N)$ pass to count grey-level frequencies.
2.  **Cumulative Sums**: Precompute lookup tables for intensity moments.
3.  **BFS Optimization**: Search for thresholds that minimize $\sigma^2_w$.
4.  **Segmentation**: Apply the winning thresholds to generate the segmented image.

## 💻 Usage
Compile and run the main class:
```bash
javac BFSMultiThreshold.java
java BFSMultiThreshold