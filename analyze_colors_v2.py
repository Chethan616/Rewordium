from PIL import Image
from collections import Counter
import colorsys

def analyze_image(image_path):
    try:
        img = Image.open(image_path)
        img = img.resize((300, 300))  # Resize for faster processing
        img = img.convert('RGB')
        
        pixels = list(img.getdata())
        total_pixels = len(pixels)
        
        counts = Counter(pixels)
        most_common = counts.most_common(100) # Get more colors
        
        print(f"--- Analysis of {image_path} ---")
        
        backgrounds = []
        keys = []
        texts = []
        accents = []
        
        for color, count in most_common:
            r, g, b = color
            h, s, v = colorsys.rgb_to_hsv(r/255.0, g/255.0, b/255.0)
            
            # Simple HSL lightness
            l = (max(r,g,b) + min(r,g,b)) / 2.0 / 255.0
            
            percentage = (count / total_pixels) * 100
            hex_color = '#{:02x}{:02x}{:02x}'.format(r, g, b)
            
            info = f"Hex: {hex_color}, R:{r} G:{g} B:{b}, H:{h*360:.1f}, S:{s:.2f}, V:{v:.2f}, L:{l:.2f}, Freq: {percentage:.2f}%"
            
            # Simple heuristics
            if l < 0.15: # Very dark -> Background
                backgrounds.append(info)
            elif l < 0.4: # Dark -> Keys or Accents (if saturated)
                if s > 0.2:
                    accents.append(info)
                else:
                    keys.append(info)
            elif l > 0.7: # Very light -> Text
                texts.append(info)
            else:
                # Medium L -> likely Accents or Keys
                if s > 0.3:
                    accents.append(info)
                else:
                    keys.append(info)
            
        print("\nPossible Background Colors (Darkest, High Freq):")
        for c in backgrounds[:10]: print(c)
        
        print("\nPossible Key Background Colors (Dark, High Freq):")
        for c in keys[:10]: print(c)

        print("\nPossible Text Colors (Light, High Freq):")
        for c in texts[:10]: print(c)
        
        print("\nPossible Accent Colors (Higher Saturation, Any L):")
        for c in accents[:10]: print(c)
        
    except Exception as e:
        print(f"Error analyzing image: {e}")

if __name__ == "__main__":
    analyze_image("d:/Flutter_workspace/rewordium/YC_startup-main/YC_startup-main/gboard_blue.jpeg")
