// https://github.com/react-native-community/cli/blob/main/docs/dependencies.md

module.exports = {
  dependency: {
    platforms: {
      ios: null, // Android only — no iOS implementation
      android: {
        sourceDir: './android',
        packageImportPath: 'import com.margelo.nitro.pressripple.ReactNativePressRipplePackage;',
        packageInstance: 'new ReactNativePressRipplePackage()',
      },
    },
  },
}
