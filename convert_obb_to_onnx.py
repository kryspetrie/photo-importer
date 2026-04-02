#!/usr/bin/env python3
"""
Converts YOLOv8 OBB (Oriented Bounding Box) PyTorch models to ONNX format.
This script takes .pt files downloaded from Ultralytics and exports them as
ONNX models that ONNX Runtime can load directly.

Usage:
    python3 convert_obb_to_onnx.py
"""

import sys
import os

# Suppress the NumPy 2.x warnings
import warnings
warnings.filterwarnings("ignore", message=".*NumPy 1.x.*")

import torch
from ultralytics import YOLO

RESOURCES_BASE = "/Users/krys.petrie/dev/petrie-file-importer/src/main/resources/ml_models/yolo26n-pose-onnx"

models = [
    ("yolo26n-obb.pt", "yolo26n-obb.onnx"),
    ("yolo26s-obb.pt", "yolo26s-obb.onnx"),
    ("yolo26m-obb.pt", "yolo26m-obb.onnx"),
    ("yolo26l-obb.pt", "yolo26l-obb.onnx"),
    ("yolo26x-obb.pt", "yolo26x-obb.onnx"),
]

for pt_name, onnx_name in models:
    pt_path = os.path.join(RESOURCES_BASE, pt_name)
    onnx_path = os.path.join(RESOURCES_BASE, onnx_name)

    if not os.path.exists(pt_path):
        print(f"SKIP: {pt_name} not found")
        continue

    size = os.path.getsize(pt_path) / (1024 * 1024)
    print(f"\n{'='*60}")
    print(f"Converting {pt_name} ({size:.1f} MB) -> {onnx_name}")

    try:
        # Load the model (yolo11n-obb.yaml or yolov8n-obb.yaml for architecture)
        # Try yolo11n-obb first (newer), fall back to yolov8n-obb
        try:
            model = YOLO(pt_path)
        except Exception as e:
            print(f"  Direct load failed: {e}")
            # Try loading as a pretrained ultralytics model
            base_name = pt_name.replace("-obb.pt", "")
            model = YOLO(f"{base_name}.pt")

        # Export to ONNX (opset 12 for broad compatibility)
        # dynamic=True allows variable batch/image sizes
        model.export(format="onnx", opset=12, dynamic=False, imgsz=640)

        # The exported file has .onnx extension added
        exported = pt_path.replace(".pt", ".onnx")
        if os.path.exists(exported):
            final_path = onnx_path
            os.rename(exported, final_path)
            final_size = os.path.getsize(final_path) / (1024 * 1024)
            print(f"  SUCCESS: {onnx_name} ({final_size:.1f} MB)")
        else:
            print(f"  WARNING: Export completed but file not found at {exported}")
    except Exception as e:
        print(f"  FAILED: {e}")
        import traceback
        traceback.print_exc()

print("\n" + "="*60)
print("Done!")
