module LibraryScope
  class Helper
    def call_target
      helper_inner
    end

    def helper_inner
      true
    end
  end
end
