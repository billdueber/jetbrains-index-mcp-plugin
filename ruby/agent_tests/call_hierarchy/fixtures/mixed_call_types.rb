class Worker
  def run
    prepare
  end

  def prepare
    setup
  end

  def setup
    true
  end

  def self.start
    worker = new
    worker.run
  end
end