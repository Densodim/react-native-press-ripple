export type RippleConfig = {
  /** Ripple color in #AARRGGBB format. Default: '#40000000' (black 25%) */
  color?: string
  /** Border radius of the container in dp (for clipping). Default: 0 */
  borderRadius?: number
  /**
   * Explicitly disables the ripple effect.
   * Default: false
   */
  disabled?: boolean
}
