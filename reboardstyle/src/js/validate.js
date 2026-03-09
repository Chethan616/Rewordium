// ReboardStyle - Validate themes
// Licensed under Apache 2.0

const path = require('path')
const fs = require('fs').promises
const { logError, logInfo, logOk } = require('./utils')

const inputDir = path.join(__dirname, '../_output')
const extJson = path.join(inputDir, 'extension.json')
const stylesheetsDir = path.join(inputDir, 'stylesheets')

const validate = async () => {
	try {
		const files = (await fs.readdir(stylesheetsDir)).filter((f) => f.endsWith('.json'))
		const fileSet = new Set(files)
		const ext = JSON.parse(await fs.readFile(extJson, 'utf8'))
		const themeIds = Array.isArray(ext.themes) ? ext.themes.map((t) => t.id) : []
		const expectedFiles = themeIds.map((id) => `${id}.json`)
		const expectedSet = new Set(expectedFiles)
		const missingFiles = expectedFiles.filter((f) => !fileSet.has(f))
		const extraFiles = files.filter((f) => !expectedSet.has(f))

		if (files.length !== themeIds.length) {
			logError(`Mismatch: ${files.length} stylesheets vs ${themeIds.length} themes`)
			if (missingFiles.length) {
				logError('Missing stylesheets:')
				missingFiles.forEach((f) => logError('  - ' + f))
			}
			if (extraFiles.length) {
				logError('Extra stylesheets (no matching theme):')
				extraFiles.forEach((f) => logError('  - ' + f))
			}
			process.exit(1)
		}

		// Validate each stylesheet is valid JSON
		for (const file of files) {
			try {
				const content = await fs.readFile(path.join(stylesheetsDir, file), 'utf8')
				JSON.parse(content)
			} catch (e) {
				logError(`Invalid JSON in ${file}: ${e.message}`)
				process.exit(1)
			}
		}

		logOk(`Validation passed: ${themeIds.length} themes, all stylesheets valid`)
	} catch (e) {
		logError('Validation failed: ' + (e?.message || e))
		process.exit(1)
	}
}

validate()
