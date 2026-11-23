// Theme and board-colour data (plain values, no React), used by theme.tsx and ThemePicker.

export type ThemeName = 'club' | 'auto' | 'light' | 'dark' | 'wood' | 'navy'
export type BoardName = 'theme' | 'green' | 'brown' | 'blue' | 'grey' | 'purple'

export const THEMES: { name: ThemeName; label: string; swatch: [string, string] }[] = [
  { name: 'club', label: 'Club', swatch: ['#26272b', '#7fa650'] },  // dark with a green board (the default)
  { name: 'auto', label: 'Auto', swatch: ['#f3f4f6', '#15171c'] }, // follows the device's light/dark setting
  { name: 'light', label: 'Light', swatch: ['#ffffff', '#2f6fde'] },
  { name: 'dark', label: 'Dark', swatch: ['#1e2128', '#4c8dff'] },
  { name: 'wood', label: 'Wood', swatch: ['#faf5ec', '#9a5a2b'] },
  { name: 'navy', label: 'Navy', swatch: ['#142039', '#38bdf8'] },
]

/** Light and dark square colours for each board style. */
export const BOARDS: Record<Exclude<BoardName, 'theme'>, { label: string; light: string; dark: string }> = {
  green: { label: 'Green', light: '#ebecd0', dark: '#779556' },
  brown: { label: 'Brown', light: '#f0d9b5', dark: '#b58863' },
  blue: { label: 'Blue', light: '#dee3e6', dark: '#8ca2ad' },
  grey: { label: 'Grey', light: '#e6e6e6', dark: '#a3a7ad' },
  purple: { label: 'Purple', light: '#efeaf6', dark: '#8f7bb3' },
}

/** Each theme's own board, used when the board choice is "Match theme". */
export const THEME_BOARD: Record<Exclude<ThemeName, 'auto'>, Exclude<BoardName, 'theme'>> = {
  club: 'green',
  light: 'blue',
  dark: 'green',
  wood: 'brown',
  navy: 'blue',
}
