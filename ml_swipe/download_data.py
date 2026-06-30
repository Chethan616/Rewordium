import os
from datasets import load_dataset

def main():
    print("Downloading futo-org/swipe.futo.org dataset...")
    # Using streaming=True if the dataset is large, or just download it if it's manageable.
    # The FUTO dataset has 1M+ swipes.
    try:
        # Load the dataset
        dataset = load_dataset("futo-org/swipe.futo.org", trust_remote_code=True)
        print("Dataset loaded successfully!")
        
        # Save to local disk for offline training
        save_path = os.path.join(os.path.dirname(__file__), "data", "swipe.futo.org")
        print(f"Saving to {save_path}...")
        dataset.save_to_disk(save_path)
        print("Done!")
    except Exception as e:
        print(f"Failed to load dataset: {e}")

if __name__ == "__main__":
    main()
