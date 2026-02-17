// ReboardStyle - Compress _output to .flex file
// Licensed under Apache 2.0

const path = require('path')
const fs = require('fs').promises
const fsSync = require('fs')
const { logInfo, logError, logOk, updateVersion, zipFolder } = require('./utils')
const config = require('../config')

const getArg = (flag) => {
	const arg = process.argv.find((a) => a.startsWith(`--${flag}=`))
	return arg ? arg.split('=')[1] : null
}

const main = async () => {
	try {
		const verbose = process.argv.includes('--verbose')
		const version = config.VERSION
		const isDebug = config.isDebug

		const inputDir = getArg('input') || path.join(__dirname, '../_output')
		const outDir = getArg('outdir') || (isDebug
			? path.resolve(__dirname, '../../debug')
			: path.resolve(__dirname, '../../build'))

		const outFile = isDebug
			? `reboardstyle-v${version}-${config.CHANGE_NAME || 'debug'}.flex`
			: `reboardstyle-v${version}.flex`

		const outputFlex = path.join(outDir, outFile)
		const extJson = path.join(inputDir, 'extension.json')

		if (!fsSync.existsSync(inputDir)) {
			logError('Input directory missing: ' + inputDir)
			logError('Ensure src/_output contains extension.json + stylesheets/')
			process.exit(1)
		}
		if (!fsSync.existsSync(extJson)) {
			logError('extension.json not found in: ' + inputDir)
			process.exit(1)
		}

		await updateVersion(inputDir, version)
		await zipFolder(inputDir, outputFlex)
		logOk(`Build complete: ${outputFlex}`)

		if (verbose) {
			const extRaw = await fs.readFile(extJson, 'utf8')
			const ext = JSON.parse(extRaw)
			const numThemes = Array.isArray(ext.themes) ? ext.themes.length : 0
			const stats = await fs.stat(outputFlex)
			logInfo(`${numThemes} themes | ${(stats.size / 1024).toFixed(1)} KB`)
		}
	} catch (err) {
		logError('Compress failed: ' + (err?.message || err))
		process.exit(1)
	}
}

main()
