# ReboardStyle — Material You Keyboard Themes

Original Material You keyboard themes for **ReBoard**, designed with Gboard-inspired shapes and Material 3 dynamic color system.

## Themes

| Theme | Mode | Style |
|-------|------|-------|
| ReBoard Material Light | Day | Bordered |
| ReBoard Material Light Borderless | Day | Borderless |
| ReBoard Material Dark | Night | Bordered |
| ReBoard Material Dark Borderless | Night | Borderless |
| ReBoard Material AMOLED | Night | Bordered (Pure Black) |
| ReBoard Material AMOLED Borderless | Night | Borderless (Pure Black) |

**6 themes** total — covers all mode and style combinations.

## Design

- **Material You dynamic colors** — adapts to your wallpaper
- **Gboard-inspired key shapes** — 12dp rounded corners, pill-shaped space bar
- **Material 3 surface hierarchy** — uses `surfaceContainer` tokens for proper layering
- **Enter key** — uses `primaryContainer` with larger radius
- **Shift key** — tertiary color indicator for caps lock state
- **Backspace** — subtle distinct styling
- **Better elevation** — shadow-elevation for bordered keys, clean flat for borderless

## Setup

### Install dependencies

```bash
npm install
```

### Build .flex

```bash
npm run build
```

Output: `build/reboardstyle-v1.0.0.flex`

### Import

1. Open ReBoard settings
2. Go to Theme
3. Import the `.flex` file

### Other commands

```bash
npm run validate         # Check stylesheet/theme consistency
npm run compress         # Build without validation
npm run decompress       # Extract .flex to src/_output
```

## Structure

```
reboardstyle/
├── package.json
├── LICENSE                    # Apache 2.0
├── README.md
├── src/
│   ├── config.js
│   ├── _output/               # Theme source files
│   │   ├── extension.json
│   │   └── stylesheets/
│   │       ├── reboard_material_light.json
│   │       ├── reboard_material_light_borderless.json
│   │       ├── reboard_material_dark.json
│   │       ├── reboard_material_dark_borderless.json
│   │       ├── reboard_material_amoled.json
│   │       └── reboard_material_amoled_borderless.json
│   └── js/                    # Build tooling
│       ├── compress.js
│       ├── decompress.js
│       ├── validate.js
│       ├── shared.js
│       └── utils/
│           └── index.js
└── build/                     # Generated .flex output
```

## License

Apache License 2.0 — see [LICENSE](LICENSE)
