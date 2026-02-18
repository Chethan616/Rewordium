from PIL import Image
from collections import Counter
import math

def get_dominant_colors(image_path, num_colors=10):
    try:
        img = Image.open(image_path)
        img = img.resize((150, 150))  # Resize for faster processing
        img = img.convert('RGB')
        
        pixels = list(img.getdata())
        total_pixels = len(pixels)
        
        # Simple color counting
        counts = Counter(pixels)
        
        # Sort by frequency
        most_common = counts.most_common(num_colors)
        
        print(f"Top {num_colors} colors in {image_path}:")
        for color, count in most_common:
            hex_color = '#{:02x}{:02x}{:02x}'.format(*color)
            percentage = (count / total_pixels) * 100
            print(f"Hex: {hex_color}, RGB: {color}, Frequency: {percentage:.2f}%")

    except Exception as e:
        print(f"Error analyzing image: {e}")

if __name__ == "__main__":
    get_dominant_colors("d:/Flutter_workspace/rewordium/YC_startup-main/YC_startup-main/gboard_blue.jpeg")
