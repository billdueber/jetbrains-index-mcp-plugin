class ScopeLanguageTarget
  def target
    true
  end
end

def call_target
  ScopeLanguageTarget.new.target
end
