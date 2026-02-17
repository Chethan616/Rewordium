// ReboardStyle - Decompress .flex file to _output
// Licensed under Apache 2.0

const path = require('path')
const fs = require('fs').promises
const fsSync = require('fs')
const { logInfo, logError, unzipFlex } = require('./utils')

const getArg = (flag) => {
	const arg = process.argv.find((a) => a.startsWith(`--${flag}=`))
	return arg ? arg.split('=')[1] : null
}

const findLatestFlex = async (dir) => {
	if (!fsSync.existsSync(dir)) return null
	const files = (await fs.readdir(dir)).filter((f) => f.endsWith('.flex'))
	if (!files.length) return null
	const stats = await Promise.all(
		files.map(async (f) => ({
			file: f,
			mtime: (await fs.stat(path.join(dir, f))).mtime,
		}))
	)
	return stats.sort((a, b) => b.mtime - a.mtime)[0].file
}

const main = async () => {
	try {
		const inputDir = getArg('input') || path.join(__dirname, '../_input')
		const outputDir = getArg('output') || path.join(__dirname, '../_output')

		const latestFlex = await findLatestFlex(inputDir)
		if (!latestFlex) {
			logError('No .flex file found in ' + inputDir)
			logError('Place a .flex file in src/_input/ first')
			process.exit(1)
		}

		const inFile = path.join(inputDir, latestFlex)
		await unzipFlex(inFile, outputDir)

		logInfo(`Decompressed: ${latestFlex}`)
	} catch (err) {
		logError('Decompress failed: ' + (err?.message || err))
		process.exit(1)
	}
}

main()
