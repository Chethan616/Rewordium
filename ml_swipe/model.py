import torch
import torch.nn as nn
import torch.nn.functional as F

class SpatialBasisHead(nn.Module):
    """
    Evaluates predictions through a continuous spatial basis function evaluated at runtime coordinates.
    This allows the model to adapt to any layout on the fly.
    """
    def __init__(self, hidden_dim, num_bases=16):
        super().__init__()
        self.num_bases = num_bases
        # Map from RNN hidden state to basis coefficients
        self.hidden_to_coeffs = nn.Linear(hidden_dim, num_bases)
        
        # We can use a DCT-style basis (e.g. cos(k*x), sin(k*y), etc)
        # or learnable basis weights. For a simple continuous function,
        # we can just use a small MLP that takes (x, y) and outputs 'num_bases' values.
        self.coordinate_basis = nn.Sequential(
            nn.Linear(2, 32),
            nn.ReLU(),
            nn.Linear(32, num_bases)
        )

    def forward(self, hidden_seq, layout_coords):
        """
        hidden_seq: [batch, seq_len, hidden_dim]
        layout_coords: [batch, num_keys, 2] - The (x, y) coordinates of each key
        
        Returns:
        logits: [batch, seq_len, num_keys]
        """
        # [batch, seq_len, num_bases]
        coeffs = self.hidden_to_coeffs(hidden_seq)
        
        # Evaluate basis functions at the key coordinates
        # [batch, num_keys, num_bases]
        basis_vals = self.coordinate_basis(layout_coords)
        
        # Dot product of coefficients and basis values
        # [batch, seq_len, num_bases] @ [batch, num_bases, num_keys] -> [batch, seq_len, num_keys]
        logits = torch.bmm(coeffs, basis_vals.transpose(1, 2))
        return logits

class LayoutAgnosticEncoder(nn.Module):
    """
    Layout-Agnostic Neural Swipe Encoder inspired by FUTO.
    """
    def __init__(self, input_dim=3, hidden_dim=256, num_layers=2, add_qwerty_boost=True):
        super().__init__()
        # Input features: typically (dx, dy, dt) or (x, y, t)
        self.rnn = nn.LSTM(input_dim, hidden_dim, num_layers, batch_first=True, bidirectional=True)
        
        # Project bidirectional hidden state down
        self.proj = nn.Linear(hidden_dim * 2, hidden_dim)
        
        self.spatial_head = SpatialBasisHead(hidden_dim)
        
        # Step 6: Layout-specific decoder boost (optional)
        # For our highest-traffic layout (QWERTY), we can train an extra specific head
        # to push peak accuracy above the general encoder.
        self.add_qwerty_boost = add_qwerty_boost
        if add_qwerty_boost:
            # Assume 30 keys for standard QWERTY
            self.qwerty_head = nn.Linear(hidden_dim, 30)
        
    def forward(self, trajectory, layout_coords, is_qwerty=False):
        """
        trajectory: [batch, seq_len, input_dim]
        layout_coords: [batch, num_keys, 2]
        """
        out, _ = self.rnn(trajectory)
        out = F.relu(self.proj(out))
        
        logits = self.spatial_head(out, layout_coords)
        
        if self.add_qwerty_boost and is_qwerty:
            # Combine the agnostic logits with the highly-optimized layout-specific logits
            # This requires layout_coords to match the exact 30-key QWERTY ordering.
            qwerty_logits = self.qwerty_head(out)
            # Simple addition or weighted sum
            logits = logits + 0.5 * qwerty_logits
            
        return logits
