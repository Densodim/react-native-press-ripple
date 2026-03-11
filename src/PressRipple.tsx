import { useCallback, useEffect, useRef } from 'react'
import { findNodeHandle, View, type GestureResponderEvent } from 'react-native'
import { createPressRipple, type PressRipple as PressRippleSpec } from './specs/PressRipple.nitro'
import type { RippleConfig } from './types'

const DEFAULT_COLOR = '#40000000' // black 25% in #AARRGGBB
const DEFAULT_BORDER_RADIUS = 0

/**
 * Hook that returns onPressIn + hostRef for M3 Material ripple effect.
 *
 * - hostRef: attach to your Pressable (or any View) as `ref`
 * - onPressIn: pass to Pressable's onPressIn
 * - No extra view component to render — overlay is managed natively
 * - Zero JS-thread overhead after initial setup
 *
 * Usage:
 *   const ripple = usePressRipple({ color: '#73ffffff', borderRadius: 8 })
 *
 *   <Pressable ref={ripple.hostRef} onPressIn={ripple.onPressIn}>
 *     {children}
 *   </Pressable>
 */
export const usePressRipple = (config?: RippleConfig) => {
  const color = config?.color ?? DEFAULT_COLOR
  const borderRadius = config?.borderRadius ?? DEFAULT_BORDER_RADIUS
  const disabled = config?.disabled ?? false

  // Create HybridObject once — stable reference across renders
  const ripple = useRef<PressRippleSpec | null>(null)
  if (ripple.current === null) {
    const obj = createPressRipple()
    obj.color = color
    obj.borderRadius = borderRadius
    ripple.current = obj
  }

  // Sync color changes to native
  useEffect(() => {
    if (ripple.current) ripple.current.color = color
  }, [color])

  // Sync borderRadius changes to native
  useEffect(() => {
    if (ripple.current) ripple.current.borderRadius = borderRadius
  }, [borderRadius])

  // Detach when hook unmounts
  useEffect(() => {
    return () => {
      ripple.current?.detachFromView()
    }
  }, [])

  /**
   * Ref callback — attach/detach the native ripple overlay.
   * Pass this as `ref` to your Pressable or wrapping View.
   */
  const hostRef = useCallback((view: View | null) => {
    if (view) {
      const tag = findNodeHandle(view)
      if (tag != null && ripple.current) {
        ripple.current.attachToView(tag)
      }
    } else {
      ripple.current?.detachFromView()
    }
  }, [])

  const onPressIn = useCallback(
    (event: GestureResponderEvent) => {
      if (disabled) return
      const { locationX, locationY } = event.nativeEvent
      ripple.current?.triggerRipple(locationX, locationY)
    },
    [disabled],
  )

  return { onPressIn, hostRef }
}
