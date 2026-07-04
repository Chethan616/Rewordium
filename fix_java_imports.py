import os
import glob

folder = r'c:\Users\cheth\OneDrive\Desktop\PC2\rewordium\rewordium\rewordium\rewordium\android\reboard_keyboard\src\main\kotlin\com\noxquill\rewordium\keyboard\app\settings\stickerstudio'

for file in glob.glob(os.path.join(folder, '*.java')):
    with open(file, 'r', encoding='utf-8') as f:
        c = f.read()
    
    if 'com.bumptech.glide.gifdecoder' in c:
        c = c.replace('com.bumptech.glide.gifdecoder', 'com.noxquill.rewordium.keyboard.app.settings.stickerstudio')
        with open(file, 'w', encoding='utf-8') as f:
            f.write(c)
        print(f"Fixed {os.path.basename(file)}")

