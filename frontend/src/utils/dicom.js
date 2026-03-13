let dicomPromise = null
let cornerstoneRef = null

export const loadDicom = async () => {
  if (dicomPromise) return dicomPromise
  dicomPromise = (async () => {
    const [cornerstoneMod, dicomParserMod, wadoMod] = await Promise.all([
      import('cornerstone-core'),
      import('dicom-parser'),
      import('cornerstone-wado-image-loader')
    ])
    const cornerstone = cornerstoneMod.default ?? cornerstoneMod
    const dicomParser = dicomParserMod.default ?? dicomParserMod
    const cornerstoneWADOImageLoader = wadoMod.default ?? wadoMod
    cornerstoneWADOImageLoader.external.cornerstone = cornerstone
    cornerstoneWADOImageLoader.external.dicomParser = dicomParser
    cornerstoneWADOImageLoader.configure({ useWebWorkers: false })
    cornerstoneRef = cornerstone
    return { cornerstone }
  })()
  return dicomPromise
}

export const getCornerstone = () => cornerstoneRef
