# react-native-press-ripple

Native Android **M3 Material ripple** effect built with **Nitro Modules**.

Zero JS-thread overhead — the Canvas overlay is attached programmatically to your Pressable, and all animation runs on the native thread via `ValueAnimator`. No Reanimated, no bridge delays, no extra view component to render.

---

## Why not Reanimated?

The standard approach with Reanimated still has a bottleneck:

```
onPressIn → JS setState → bridge → React render → mount Animated.View → useEffect → animation starts
```

This cycle takes **50–150ms** on mid-range devices — visible as a lag before the effect begins.

`react-native-press-ripple` eliminates this:

```
onPressIn → JSI call → Kotlin triggerRipple(x, y) → ValueAnimator.start() → onDraw()
```

The ripple starts in **< 1ms** after the touch. The Nitro JSI bridge is synchronous — no async round-trip.

---

## Platform support

| Platform | Support |
|----------|---------|
| Android | ✅ Nitro HybridObject + Canvas + ValueAnimator |
| iOS | — (planned) |

---

## Requirements

| Dependency | Version |
|------------|---------|
| `react-native` | `>= 0.73.0` |
| `react` | `>= 18.0.0` |
| `react-native-nitro-modules` | `>= 0.18.0` |

New Architecture (Fabric) is **required**. Old Architecture is not supported.

---

## Installation

```bash
# npm
npm install react-native-press-ripple react-native-nitro-modules

# yarn
yarn add react-native-press-ripple react-native-nitro-modules

# bun
bun add react-native-press-ripple react-native-nitro-modules
```

Nitro autolinking registers `HybridPressRipple` automatically — no manual native changes needed.

Rebuild the native project:

```bash
# React Native CLI
npx react-native run-android

# Expo
npx expo run:android
```

---

## Quick start

```tsx
import { Pressable, Text } from 'react-native'
import { usePressRipple } from 'react-native-press-ripple'

export const MyButton = () => {
  const ripple = usePressRipple({
    color: '#40000000',  // black 25% opacity (#AARRGGBB)
    borderRadius: 8,
  })

  return (
    <Pressable ref={ripple.hostRef} onPressIn={ripple.onPressIn} style={styles.button}>
      <Text>Press me</Text>
    </Pressable>
  )
}
```

> No `<ripple.View />` needed inside — the ripple overlay is attached to your Pressable natively.

---

## API

### `usePressRipple(config?)`

Returns `{ onPressIn, hostRef }`.

```tsx
const ripple = usePressRipple(config?)
```

#### Config

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `color` | `string` | `'#40000000'` | Ripple color in **`#AARRGGBB`** format. Alpha comes first. |
| `borderRadius` | `number` | `0` | Border radius of the host container in **dp**. Clips the ripple to rounded corners. Must match your button's `borderRadius`. |
| `disabled` | `boolean` | `false` | When `true` — `onPressIn` is a no-op, no ripple fires. |

#### Returns

| Key | Type | Description |
|-----|------|-------------|
| `hostRef` | `(view: View \| null) => void` | Ref callback — attach to your `Pressable` or wrapping `View`. Triggers native overlay attachment. |
| `onPressIn` | `(event: GestureResponderEvent) => void` | Pass to `onPressIn` of your `Pressable`. Triggers the ripple animation via JSI. |

---

## Usage patterns

### Basic button

```tsx
const ripple = usePressRipple({ color: '#40000000', borderRadius: 8 })

<Pressable ref={ripple.hostRef} onPressIn={ripple.onPressIn} style={styles.button}>
  <Text>Submit</Text>
</Pressable>
```

### Dark background — white ripple

```tsx
const ripple = usePressRipple({
  color: '#73ffffff',  // white 45%
  borderRadius: 10,
})
```

### Disabled state

```tsx
const ripple = usePressRipple({
  color: '#40000000',
  disabled: Boolean(isDisabled),
})
```

When `disabled: true` — `onPressIn` returns immediately. No ripple, no native call.

### Combining with your own `onPressIn`

```tsx
const ripple = usePressRipple({ color: '#40000000', borderRadius: 8 })

const handlePressIn = useCallback(
  (event: GestureResponderEvent) => {
    ripple.onPressIn(event)       // ripple first (JSI, ~0ms)
    analytics.track('button_press')
  },
  [ripple.onPressIn],
)

<Pressable ref={ripple.hostRef} onPressIn={handlePressIn}>
  <Text>Press</Text>
</Pressable>
```

### Conditional by variant

```tsx
const ripple = usePressRipple({
  color: variant === 'primary' ? '#73ffffff' : '#40000000',
  borderRadius: BORDER_RADIUS[size],
  disabled: variant === 'ghost',
})
```

---

## Color format

Android `Color.parseColor()` uses **`#AARRGGBB`** — alpha comes **first**, not last.

```
#AARRGGBB
 ^^         — Alpha (00 = transparent, FF = opaque)
   ^^       — Red
     ^^     — Green
       ^^   — Blue
```

### Common values

| Color | Hex |
|-------|-----|
| Black 25% | `#40000000` |
| Black 15% | `#26000000` |
| White 45% | `#73ffffff` |
| White 30% | `#4dffffff` |
| Brand 30% | `#4d007AFF` |

> ⚠️ Do **not** use CSS `rgba(0,0,0,0.25)` — Android will reject it and fall back to default.

---

## How it works

### JS side

1. `usePressRipple` creates a **Nitro HybridObject** (`PressRipple`) once per hook instance.
2. `hostRef` — on mount, calls `pressRipple.attachToView(reactTag)` via JSI.
   Kotlin finds the native view with `decorView.findViewById(reactTag)` and adds a `PressRippleView` as the topmost child (MATCH_PARENT, renders above content).
3. `onPressIn` — calls `pressRipple.triggerRipple(x, y)` via JSI synchronously.
   Kotlin dispatches `startRipple()` to the main thread. No React re-render, no prop update.
4. On unmount — `detachFromView()` removes the overlay.

### Native side (Kotlin)

```
triggerRipple(x, y)
  → main thread: PressRippleView.startRipple(x, y)
      converts dp → px (cached density)
      calcMaxRadius to farthest corner
      AnimatorSet:
        Phase 1: radius 0 → maxRadius + alpha 0 → target  (80ms, PropertyValuesHolder)
        Phase 2: radius continues → maxRadius              (270ms)
        Phase 3: alpha → 0                                 (250ms, 80ms delay)
      each frame: invalidate() → onDraw()
        canvas.clipPath(roundRect)    ← respects borderRadius
        canvas.drawCircle(x, y, r)
```

`LAYER_TYPE_HARDWARE` ensures GPU-composited rendering.

### Architecture

```
JS Thread (JSI)                    Main Thread (Android)
──────────────────────────         ──────────────────────────────
hostRef(view)
  → pressRipple.attachToView(tag)  → decorView.findViewById(tag)
                                      hostView.addView(overlay)

onPressIn fires
  → pressRipple.triggerRipple(x,y) → overlay.startRipple(x, y)
                                        ValueAnimator.start()
                                        ↓ every frame (~16ms)
                                        onDraw(canvas)
                                          clipPath(borderRadius)
                                          drawCircle(x, y, r)
```

No bridge, no React re-render per frame — the JS thread is free after the initial JSI call.

---

## Troubleshooting

### Ripple doesn't appear

- Confirm `react-native-nitro-modules` is installed in the app (peer dependency).
- Confirm New Architecture is enabled (`newArchEnabled=true` in `gradle.properties`).
- Rebuild native project after installation — autolinking runs at build time.

### Ripple overflows rounded corners

`borderRadius` in config must match your container's style value **in dp**:

```tsx
// Button has: style={{ borderRadius: 12 }}
const ripple = usePressRipple({ borderRadius: 12 })
```

### Color looks wrong

Remember `#AARRGGBB` (alpha first):

```ts
color: '#00000040'  // ❌ CSS format — alpha at the end
color: '#40000000'  // ✅ Android format — alpha at the start
```

### Ripple appears below content

This shouldn't happen — the overlay is added as the last child of the Pressable (highest z-order). If content is elevated via `elevation` or `zIndex`, wrap it in a `View` with matching elevation.

### `hostRef` not attaching

Make sure `hostRef` is passed as `ref` to a **native** view or Pressable, not a custom component without `forwardRef`. Functional components need `forwardRef` for refs to work.

---

## Project structure

```
react-native-press-ripple/
├── src/
│   ├── index.ts                         # Public API
│   ├── PressRipple.tsx                  # usePressRipple hook
│   ├── specs/
│   │   └── PressRipple.nitro.ts         # Nitro HybridObject spec
│   └── types/
│       └── index.ts                     # RippleConfig
├── android/
│   ├── CMakeLists.txt                   # C++ bridge (includes nitrogen cmake)
│   ├── build.gradle                     # Android library config
│   └── src/main/java/com/margelo/nitro/pressripple/
│       ├── HybridPressRipple.kt         # Nitro HybridObject — view attachment + trigger
│       └── PressRippleView.kt           # Pure Android Canvas + ValueAnimator
├── nitrogen/
│   └── generated/                       # ⛔ DO NOT EDIT — regenerated by npx nitrogen
├── nitro.json                           # Nitro autolinking config
└── package.json
```

---

## License

MIT © milautonomos
