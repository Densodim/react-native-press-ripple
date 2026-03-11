import { type HybridObject, NitroModules } from 'react-native-nitro-modules'

/**
 * M3 Material ripple effect — Nitro HybridObject.
 *
 * Programmatically attaches a native Canvas overlay to a host view (Pressable).
 * All animation runs on the native thread via ValueAnimator — zero JS-thread overhead.
 *
 * Do NOT use directly — use the usePressRipple() hook instead.
 */
export interface PressRipple extends HybridObject<{ android: 'kotlin' }> {
  /** Ripple color in #AARRGGBB format. Default: '#40000000' (black 25%) */
  color: string
  /** Container border radius in dp for clipping. Default: 0 */
  borderRadius: number

  /**
   * Attach the native Canvas ripple overlay to the view with the given React tag.
   * React Native sets each native view's Android ID equal to its React tag,
   * so this finds the view via decorView.findViewById(hostViewTag).
   */
  attachToView(hostViewTag: number): void

  /** Remove the ripple overlay and release the native view reference. */
  detachFromView(): void

  /**
   * Trigger a ripple animation at the given touch coordinates (in dp).
   * @param x GestureResponderEvent.nativeEvent.locationX
   * @param y GestureResponderEvent.nativeEvent.locationY
   */
  triggerRipple(x: number, y: number): void
}

/**
 * Factory function — creates a new PressRipple HybridObject per Pressable instance.
 * Used internally by usePressRipple().
 */
export const createPressRipple = () =>
  NitroModules.createHybridObject<PressRipple>('PressRipple')
