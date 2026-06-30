import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader
from model import LayoutAgnosticEncoder
import numpy as np

def apply_joint_augmentation(trajectory, layout_coords):
    """
    Applies joint geometric augmentation (translate, rotate, scale, shear)
    to BOTH the trajectory and the layout coordinates simultaneously.
    """
    tx, ty = np.random.uniform(-0.1, 0.1, 2)
    sx, sy = np.random.uniform(0.9, 1.1, 2)
    theta = np.random.uniform(-np.pi/18, np.pi/18)
    shx, shy = np.random.uniform(-0.1, 0.1, 2)
    
    cos_t, sin_t = np.cos(theta), np.sin(theta)
    S = np.array([[sx, 0], [0, sy]])
    Sh = np.array([[1, shx], [shy, 1]])
    R = np.array([[cos_t, -sin_t], [sin_t, cos_t]])
    
    T = R @ Sh @ S
    T_tensor = torch.tensor(T, dtype=torch.float32)
    
    aug_trajectory = trajectory.clone()
    aug_trajectory[:, :2] = aug_trajectory[:, :2] @ T_tensor
    aug_trajectory[:, 0] += tx
    aug_trajectory[:, 1] += ty
    
    aug_layout = layout_coords.clone()
    aug_layout = aug_layout @ T_tensor
    aug_layout[:, 0] += tx
    aug_layout[:, 1] += ty
    
    return aug_trajectory, aug_layout

class SwipeDataset(torch.utils.data.Dataset):
    def __init__(self, size=1000, seq_len=50, num_keys=30):
        self.size = size
        self.seq_len = seq_len
        self.num_keys = num_keys

    def __len__(self):
        return self.size

    def __getitem__(self, idx):
        trajectory = torch.randn(self.seq_len, 3) 
        layout_coords = torch.rand(self.num_keys, 2)
        trajectory, layout_coords = apply_joint_augmentation(trajectory, layout_coords)
        target = torch.randint(0, self.num_keys, (self.seq_len,))
        return trajectory, layout_coords, target

def train():
    print("Initializing Layout-Agnostic Swipe Encoder...")
    model = LayoutAgnosticEncoder(input_dim=3, hidden_dim=256)
    optimizer = optim.Adam(model.parameters(), lr=1e-3)
    criterion = nn.CrossEntropyLoss()
    
    dataset = SwipeDataset()
    dataloader = DataLoader(dataset, batch_size=32, shuffle=True)
    
    epochs = 2
    print("Starting training loop...")
    for epoch in range(epochs):
        model.train()
        total_loss = 0
        for batch_idx, (traj, layout, target) in enumerate(dataloader):
            optimizer.zero_grad()
            
            # Forward pass: [batch, seq_len, num_keys]
            logits = model(traj, layout)
            
            # Reshape for CrossEntropy: (batch * seq_len, num_keys)
            loss = criterion(logits.view(-1, logits.size(-1)), target.view(-1))
            
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            
            if batch_idx % 10 == 0:
                print(f"Epoch {epoch+1}/{epochs}, Batch {batch_idx}, Loss: {loss.item():.4f}")
                # Verify that it produces per-key probability sequences
                probs = torch.softmax(logits, dim=-1)
                print(f"  -> Generated prob sequence shape: {probs.shape} (batch, seq, num_keys)")

    print("Training complete! Model is producing per-key probabilities.")

if __name__ == "__main__":
    train()
