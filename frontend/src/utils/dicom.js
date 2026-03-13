import cornerstone from 'cornerstone-core'
import dicomParser from 'dicom-parser'
import cornerstoneWADOImageLoader from 'cornerstone-wado-image-loader'

let initialized = false

export const initDicomLoader = () => {
  if (initialized) return
  cornerstoneWADOImageLoader.external.cornerstone = cornerstone
  cornerstoneWADOImageLoader.external.dicomParser = dicomParser
  cornerstoneWADOImageLoader.configure({ useWebWorkers: false })
  initialized = true
}

export { cornerstone }
