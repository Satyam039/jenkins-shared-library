import org.company.pipeline.UniversalPipeline

def call(Map config = [:]) {
    new UniversalPipeline(this, config).run()
}
