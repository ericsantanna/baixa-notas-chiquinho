import adapter from '@sveltejs/adapter-static';
import path from 'node:path';

const releaseFolder = path.resolve(process.cwd(), '../../resources/static');

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		adapter: adapter({
			// default options are shown. On some platforms
			// these options are set automatically — see below
			pages: releaseFolder,
			assets: releaseFolder,
			fallback: 'index.html',
			precompress: false,
			strict: true
		}),
        prerender: {
            entries: [],
        }
	}
};

export default config;
