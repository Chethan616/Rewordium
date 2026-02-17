const isDebug = false
const vNumber = '1.0.0'
const changes = 'initial-release'

const config = {
	isDebug,
	VERSION: `${vNumber}${isDebug ? '-debug' : ''}`,
	CHANGE_NAME: isDebug ? changes : '',
}

module.exports = config
