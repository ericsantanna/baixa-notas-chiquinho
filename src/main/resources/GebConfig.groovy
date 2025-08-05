import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.FirefoxProfile

println "GebConfig.groovy loaded!"

def profile = new FirefoxProfile()
profile.setPreference("browser.download.folderList", 2)
profile.setPreference("browser.download.manager.showWhenStarting", false)
profile.setPreference("browser.download.dir", "/tmp/baixaNotas/")
profile.setPreference("browser.download.useDownloadDir", true)
profile.setPreference("browser.download.panel.shown", false)
profile.setPreference("browser.helperApps.neverAsk.saveToDisk", "application/zip,application/octet-stream,application/xml,application/x-zip-compressed,application/x-download,text/xml")

driver = {
    def options = new FirefoxOptions()
    options.setProfile(profile)
    new org.openqa.selenium.firefox.FirefoxDriver(options)
}

waiting {
    timeout = 10
}
